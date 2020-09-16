/*
 * Copyright 2020 Diego Silva <diego.silva at apuntesdejava.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.apuntesdejava.mplemon.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyRep;
import static java.security.KeyRep.Type.PRIVATE;
import static java.security.KeyRep.Type.PUBLIC;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Diego Silva <diego.silva at apuntesdejava.com>
 */
@Mojo(name = "jwtprovider")
public class JwtProviderMojo extends AbstractMojo {

    private static final String[] PROVIDER_FILES = {
        "CypherService", "TokenProviderResource"
    };
    private static Map<KeyRep.Type, String> generateKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        PKCS8EncodedKeySpec encoded = new PKCS8EncodedKeySpec(publicKey.getEncoded());
        
        byte[] privateKeyString = toByte(privateKey);
        byte[] publicKeyString = toByte(encoded.getEncoded());
        Map<KeyRep.Type, String> map = new EnumMap<>(KeyRep.Type.class);
        map.put(PRIVATE, new String(privateKeyString));
        map.put(PUBLIC, new String(publicKeyString));
        return map;
    }
    static byte[] toByte(Key key) {
        return Base64.getEncoder().encode(key.getEncoded());
    }
    static byte[] toByte(byte[] content) {
        return Base64.getEncoder().encode(content);
    }

    @Parameter(
            property = "realm",
            defaultValue = "realm-example"
    )
    private String realm;

    @Parameter(
            property = "issuer",
            defaultValue = "http://apuntesdejava.com"
    )
    private String issuer;

    @Parameter(
            property = "header-key",
            defaultValue = "header-key-example"
    )
    private String headerKey;

    @Parameter(
            property = "package",
            defaultValue = "com.apuntesdejava.endpoint.secure"
    )
    private String _package;

    @Parameter(
            property = "token-valid",
            defaultValue = "100000"
    )
    private Integer tokenValid;

    @Parameter(
            property = "roles",
            defaultValue = "ADMIN,USER"
    )
    private List<String> roles;
    private String[][] dependencies = {
        {"org.bouncycastle", "bcprov-jdk15on", "1.66"},
        {"io.jsonwebtoken", "jjwt", "0.9.1"},
        {"com.fasterxml.jackson.core", "jackson-annotations", "2.11.2", "provided"},
        {"com.fasterxml.jackson.core", "jackson-core", "2.11.2", "provided"},
        {"com.fasterxml.jackson.core", "jackson-databind", "2.11.2", "provided"},
        {"jakarta.platform", "jakarta.jakartaee-web-api", "8.0.0", "provided"}
    };

    public void setPackage(String _package) {
        if (_package != null && !_package.isEmpty()) {
            this._package = _package.replace('-', '.');
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Security.addProvider(new BouncyCastleProvider());
            getLog().debug("roles:" + roles);
            for (Object item : this.getPluginContext().entrySet()) {
                Entry<String, Object> entry = (Entry<String, Object>) item;
                Object value = entry.getValue();
                getLog().debug("**" + value.getClass().toString());
                getLog().debug("--- entry:" + entry.getKey() + "-->" + entry.getValue());

            }
            MavenProject project = (MavenProject) getPluginContext().get("project");
            File baseDir = project.getBasedir();
            getLog().debug("project group:" + project.getGroupId());
            Artifact artifact = project.getArtifact();
            getLog().debug("project artifact:" + artifact.getGroupId());
            getLog().debug("project artifact:" + artifact.getArtifactId());
            File file = project.getFile();

            getLog().debug("file:" + file);
            getLog().debug("baseDir:" + baseDir);
            createClasesSecure(baseDir);
            createJwtConfig(baseDir);
            addDependencies(project);
            project.setFile(file);
        } catch (NoSuchAlgorithmException | IOException ex) {
            getLog().error(ex.getMessage(), ex);
        }

    }

    private void createClasesSecure(File baseDir) {
        getLog().debug("** Creating classes **");
        String[] path = _package.split("\\.");
        getLog().debug("package dir:" + _package);
        File packageDir = new File(baseDir, "src/main/java");
        for (String p : path) {
            packageDir = new File(packageDir, p);
        }
        getLog().debug("path secure:" + packageDir);
        packageDir.mkdirs();
        for (String fileName : PROVIDER_FILES) {
            File cypherServiceFile = new File(packageDir, fileName + ".java");
            BufferedReader br = null;
            Reader reader = null;
            PrintWriter fw = null;
            try {
                br = new BufferedReader(reader = new InputStreamReader(JwtProviderMojo.class.getResourceAsStream("/" + fileName + ".txt")));
                fw = new PrintWriter(cypherServiceFile);
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.replace("PACKAGE", _package);
                    fw.println(line);
                }
                fw.flush();
            } catch (IOException ex) {
                getLog().error(ex);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                    if (br != null) {
                        br.close();
                    }
                    if (fw != null) {
                        fw.close();
                    }
                } catch (IOException ex) {
                    getLog().error(ex);
                }
            }
        }

    }


    private void addDependencies(MavenProject project) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            XPath xPath = XPathFactory.newInstance().newXPath();
            File file = project.getFile();
            Document xmlDocument = builder.parse(file);

            getLog().debug("buscando dependencies");
            NodeList nodeList = (NodeList) xPath.compile("/project/dependencies").evaluate(xmlDocument, XPathConstants.NODESET);
            Element dependenciesElement;
            if (nodeList.getLength() == 0) {
                dependenciesElement = xmlDocument.createElement("dependencies");
                NodeList projectElements = xmlDocument.getElementsByTagName("project");
                projectElements.item(0).appendChild(dependenciesElement);
            } else {
                dependenciesElement = (Element) nodeList.item(0);
            }

            for (String[] dep : dependencies) {
                String expression = "/project/dependencies/dependency/artifactId[text()='" + dep[1] + "']";
                nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
                if (nodeList.getLength() == 0) {
                    Element dependencyElem = xmlDocument.createElement("dependency");
                    Element groupIdElem = xmlDocument.createElement("groupId");
                    groupIdElem.setTextContent(dep[0]);
                    dependencyElem.appendChild(groupIdElem);

                    Element artifactIdElem = xmlDocument.createElement("artifactId");
                    artifactIdElem.setTextContent(dep[1]);
                    dependencyElem.appendChild(artifactIdElem);

                    Element versionElem = xmlDocument.createElement("version");
                    versionElem.setTextContent(dep[2]);
                    dependencyElem.appendChild(versionElem);

                    if (dep.length > 3) {

                        Element scopeElem = xmlDocument.createElement("scope");
                        scopeElem.setTextContent(dep[3]);
                        dependencyElem.appendChild(scopeElem);
                    }
                    dependenciesElement.appendChild(dependencyElem);
                }
            }

            getLog().debug("Actualizando " + file);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(xmlDocument);
            FileWriter writer = new FileWriter(file);
            StreamResult result = new StreamResult(writer);
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(source, result);
            getLog().debug(file + " actualizado");
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException | TransformerException ex) {
            getLog().error(ex);
        }
    }


    private void createJwtConfig(File baseDir) throws NoSuchAlgorithmException, IOException {
        getLog().debug("** Creating jwt-config.json **");
        File packageDir = new File(baseDir, "src/main/resources");
        boolean createdDirs = packageDir.mkdirs();
        getLog().debug("resource dir created:" + createdDirs);
        getLog().debug("resource Dir:" + packageDir);
        File jwtConfigFile = new File(packageDir, "jwt-config.json");
        Map<KeyRep.Type, String> keys = generateKeys();
        JSONArray rolesJson = new JSONArray(roles);
        JSONObject config = new JSONObject()
                .put("issuer", this.issuer)
                .put("token-valid", this.tokenValid)
                .put("header-key", this.headerKey)
                .put("public-key", keys.get(PUBLIC))
                .put("private-key", keys.get(PRIVATE))
                .put("roles", rolesJson);
        Path path = FileSystems.getDefault().getPath(jwtConfigFile.getPath());
        Files.write(path, config.toString().getBytes());
    }

}
