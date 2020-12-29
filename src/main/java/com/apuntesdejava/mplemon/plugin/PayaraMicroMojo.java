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

import static com.apuntesdejava.mplemon.plugin.ProjectUtil.addDependencies;
import static com.apuntesdejava.mplemon.plugin.ProjectUtil.addPlugins;
import static com.apuntesdejava.mplemon.plugin.ProjectUtil.addProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Diego Silva <diego.silva at apuntesdejava.com>
 */
@Mojo(name = "add-payara-micro")
public class PayaraMicroMojo extends AbstractMojo {

    private static final String[][] MYSQL_DEPENDECIES = {
        {"mysql", "mysql-connector-java", "8.0.22"}
    };
    private static final String[][] JDBC_PLUGIN = {
        {"org.apache.maven.plugins", "maven-dependency-plugin", "3.1.2", "<executions>"
            + "                    <execution>"
            + "                        <id>copy-jdbc</id>"
            + "                        <goals>"
            + "                            <goal>copy</goal>"
            + "                        </goals>"
            + "                        <configuration>"
            + "                            <outputDirectory>target/lib</outputDirectory>"
            + "                            <stripVersion>true</stripVersion>"
            + "                            <artifactItems>"
            + "                                <artifactItem>"
            + "                                    <groupId>JDBC_GROUP</groupId>"
            + "                                    <artifactId>JDBC_ARTIFACT_ID</artifactId>"
            + "                                    <version>JDBC_VERSION</version>"
            + "                                    <type>jar</type>"
            + "                                </artifactItem>"
            + "                            </artifactItems>"
            + "                        </configuration>"
            + "                    </execution>"
            + "                </executions>"}
    };
    private static final String[][] PGSQL_DEPENDECIES = {
        {"org.postgresql", "postgresql", "42.2.18"}
    };
    private static final String CREATE_JDBC_CONNECTION_POOL = "create-jdbc-connection-pool --maxpoolsize=4 --poolresize=1 --steadypoolsize=1 --ping=true --pooling=true --restype=javax.sql.ConnectionPoolDataSource --datasourceclassname=JDBC_DRIVER --property Password=JDBC_PASSWORD:User=JDBC_USER:Url=JDBC_URL POOL_NAME";
    private static final String CREATE_JDBC_RESOURCE = "create-jdbc-resource --connectionpoolid POOL_NAME jdbc/PROJECT";
    private static final String CREATE_AUTH_REALM = "create-auth-realm --classname com.sun.enterprise.security.auth.realm.jdbc.JDBCRealm --property jaas-context=jdbcRealm:datasource-jndi=jdbc/PROJECT:user-table=USER_TABLE:user-name-column=USER_NAME_COLUMN:password-column=PASSWORD_COLUMN:group-table=GROUP_TABLE:group-name-column=GROUP_NAME_COLUMN:encoding=Hex REALM_NAME";

    private static String replaceChars(String str) {
        return str.replace(":", "\\:").replace("=", "\\=");
    }

    private static String[][] dependecies2plugin(String[][] dependecies, String[][] plugin) {
        return new String[][]{{plugin[0][0], plugin[0][1], plugin[0][2], plugin[0][3]
            .replace("JDBC_GROUP", dependecies[0][0])
            .replace("JDBC_ARTIFACT_ID", dependecies[0][1])
            .replace("JDBC_VERSION", dependecies[0][2])}};
    }

    @Parameter(defaultValue = "5.2020.7", property = "version")
    private String version;

    @Parameter(defaultValue = "/", property = "contextRoot")
    private String contextRoot;

    @Parameter(defaultValue = "1.0.7", property = "pluginVersion")
    private String pluginVersion;

    @Parameter(property = "jdbcDriver")
    private String jdbcDriver;

    @Parameter(property = "jdbcUrl")
    private String jdbcUrl;

    @Parameter(property = "jdbcUsername")
    private String jdbcUsername;

    @Parameter(property = "jdbcPassword")
    private String jdbcPassword;

    @Parameter(property = "realmGroupName", defaultValue = "roleid")
    private String realmGroupName;

    @Parameter(property = "realmGroupTable", defaultValue = "RoleUser")
    private String realmGroupTable;

    @Parameter(property = "realmPasswordColumn", defaultValue = "password")
    private String realmPasswordColumn;

    @Parameter(property = "realmUserNameColumn", defaultValue = "userName")
    private String realmUserNameColumn;

    @Parameter(property = "realmUserTable", defaultValue = "User")
    private String realmUserTable;

    private boolean useJdbc;
    private String createJdbcConnectionPool;
    private String createJdbcResource;
    private String createAuthRealm;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().debug("Add Payara Micro");
        MavenProject project = (MavenProject) getPluginContext().get("project");
        File baseDir = project.getBasedir();
//        Artifact artifact = project.getArtifact();
        String[][] props = {
            {"version.payara.micro", version}
        };
        addProperties(getLog(), project, props);
        String[][] plugins = {{
            "fish.payara.maven.plugins",
            "payara-micro-maven-plugin",
            pluginVersion,
            "<configuration>"
            + "<payaraVersion>${version.payara.micro}</payaraVersion>"
            + "                    <deployWar>true</deployWar>"
            + "                    <commandLineOptions>"
            + "                        <option>"
            + "                            <key>--autoBindHttp</key>"
            + "                        </option>"
            + "                        <option>"
            + "                            <key>--addLibs</key>"
            + "                            <value>target/lib</value>"
            + "                        </option>"
            + "                        <option>"
            + "                            <key>--prebootcommandfile</key><value>prebootcommandfile</value>"
            + "                        </option>"
            + "                        <option>"
            + "                            <key>--postbootcommandfile</key><value>postbootcommandfile</value>"
            + "                        </option>"
            + "                        <option>"
            + "                            <key>--postdeploycommandfile</key><value>postdeploycommandfile</value>"
            + "                        </option>"
            + "                    </commandLineOptions>"
            + "<contextRoot>" + contextRoot + "</contextRoot>"
            + "</configuration>"
        }};
        addPlugins(getLog(), project, plugins);
        this.useJdbc = jdbcDriver != null && !jdbcDriver.isEmpty();
        getLog().debug("usar JDBC:" + useJdbc);
        if (useJdbc) {
            addJdbc(project);
        }
        createScripts(baseDir, project);

    }

    private void createScripts(File baseDir, org.apache.maven.project.MavenProject project) {
        getLog().debug("creating scripts");
        createScriptFile(baseDir, "prebootcommandfile");
        createScriptFile(baseDir, "postbootcommandfile");
        if (useJdbc) {
            createScriptFile(baseDir, "postdeploycommandfile", createJdbcConnectionPool, createJdbcResource);
            String realm = getRealm(baseDir);
            getLog().debug("realm detectado:" + realm);
            this.createAuthRealm = CREATE_AUTH_REALM
                    .replace("PROJECT", project.getName())
                    .replace("USER_TABLE", realmUserTable)
                    .replace("USER_NAME_COLUMN", realmUserNameColumn)
                    .replace("PASSWORD_COLUMN", realmPasswordColumn)
                    .replace("GROUP_TABLE", realmGroupTable)
                    .replace("GROUP_NAME_COLUMN", realmGroupName)
                    .replace("REALM_NAME", realm);
            createScriptFile(baseDir, "postdeploycommandfile", true, createAuthRealm);

        }
    }

    private void createScriptFile(File baseDir, String scriptFileName, String... contents) {
        createScriptFile(baseDir, scriptFileName, false, contents);
    }

    private void createScriptFile(File baseDir, String scriptFileName, boolean append, String... contents) {
        try {
            File scriptFile = new File(baseDir, scriptFileName);
            boolean created = scriptFile.createNewFile();
            getLog().debug("File created " + created);
            if (contents.length > 0) {
                Files.write(scriptFile.toPath(), Arrays.asList(contents), append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException ex) {
            getLog().error("error creating " + scriptFileName + " file at " + baseDir + " directory", ex);
        }
    }

    private void addJdbc(MavenProject project) {
        String poolName = project.getName() + "Pool";
        this.createJdbcResource = CREATE_JDBC_RESOURCE.replace("POOL_NAME", poolName)
                .replace("PROJECT", project.getName());
        switch (jdbcDriver) {
            case "mysql":
                jdbcDriver = "com.mysql.cj.jdbc.MysqlConnectionPoolDataSource";
                addDependencies(getLog(), project, MYSQL_DEPENDECIES);
                addPlugins(getLog(), project, dependecies2plugin(MYSQL_DEPENDECIES, JDBC_PLUGIN));
                break;
            case "postgresql":
                jdbcDriver = "org.postgresql.jdbc3.Jdbc3ConnectionPool";
                addDependencies(getLog(), project, PGSQL_DEPENDECIES);
                addPlugins(getLog(), project, dependecies2plugin(PGSQL_DEPENDECIES, JDBC_PLUGIN));

        }
        this.createJdbcConnectionPool = CREATE_JDBC_CONNECTION_POOL
                .replace("POOL_NAME", poolName)
                .replace("JDBC_URL", replaceChars(jdbcUrl))
                .replace("JDBC_DRIVER", jdbcDriver)
                .replace("JDBC_PASSWORD", jdbcPassword)
                .replace("JDBC_USER", jdbcUsername);
    }

    private String getRealm(File baseDir) {
        try {
            Path webXmlPath = Paths.get(baseDir.toPath().toString(), "src", "main", "webapp", "WEB-INF", "web.xml");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            XPath xPath = XPathFactory.newInstance().newXPath();
            Document xmlDocument = builder.parse(webXmlPath.toFile());
            NodeList nodeList = (NodeList) xPath.compile("/web-app/login-config/realm-name").evaluate(xmlDocument, XPathConstants.NODESET);
            if (nodeList.getLength() > 0) {
                return ((Element) nodeList.item(0)).getTextContent();
            }

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException ex) {
            getLog().error(ex.getMessage(), ex);
        }
        return null;
    }
}
