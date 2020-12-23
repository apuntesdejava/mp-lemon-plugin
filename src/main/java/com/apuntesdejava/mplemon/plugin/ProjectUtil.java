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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author Diego Silva <diego.silva at apuntesdejava.com>
 */
public class ProjectUtil {

    private static void updatePomFile(Log log, File file, Document xmlDocument) throws TransformerConfigurationException, IOException, TransformerException {
        log.debug("Actualizando " + file);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(xmlDocument);
        FileWriter writer = new FileWriter(file);
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);
        log.debug(file + " actualizado");
    }

    public static void addProperties(Log log, MavenProject project, String[][] properties) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            XPath xPath = XPathFactory.newInstance().newXPath();
            File file = project.getFile();
            Document xmlDocument = builder.parse(file);

            log.debug("buscando properties");
            NodeList nodeList = (NodeList) xPath.compile("/project/properties").evaluate(xmlDocument, XPathConstants.NODESET);
            Element propertiesElement;
            if (nodeList.getLength() == 0) {
                propertiesElement = xmlDocument.createElement("properties");
                NodeList projectElements = xmlDocument.getElementsByTagName("project");
                projectElements.item(0).appendChild(propertiesElement);
            } else {
                propertiesElement = (Element) nodeList.item(0);
            }

            for (String[] prop : properties) {
                String expression = "/project/properties/" + prop[0];
                nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
                if (nodeList.getLength() == 0) {
                    Element propElem = xmlDocument.createElement(prop[0]);
                    propElem.setTextContent(prop[1]);

                    propertiesElement.appendChild(propElem);
                }
            }
            updatePomFile(log, file, xmlDocument);
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException | TransformerException ex) {
            log.error(ex);
        }
    }

    public static void addPlugins(Log log, MavenProject project, String[][] plugins) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            XPath xPath = XPathFactory.newInstance().newXPath();
            File file = project.getFile();
            Document xmlDocument = builder.parse(file);

            log.debug("buscando plugins");
            NodeList nodeList = (NodeList) xPath.compile("/project/build").evaluate(xmlDocument, XPathConstants.NODESET);
            Element buildElement;
            if (nodeList.getLength() == 0) {
                buildElement = xmlDocument.createElement("build");
                NodeList projectElements = xmlDocument.getElementsByTagName("project");
                projectElements.item(0).appendChild(buildElement);
            } else {
                buildElement = (Element) nodeList.item(0);
            }
            nodeList = (NodeList) xPath.compile("/project/build/plugins").evaluate(xmlDocument, XPathConstants.NODESET);
            Element pluginsElement;
            if (nodeList.getLength() == 0) {
                pluginsElement = xmlDocument.createElement("plugins");
                buildElement.appendChild(pluginsElement);
            } else {
                pluginsElement = (Element) nodeList.item(0);
            }

            for (String[] dep : plugins) {
                String expression = "/project/build/plugins/plugin/artifactId[text()='" + dep[1] + "']";
                nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
                if (nodeList.getLength() == 0) {
                    Element pluginElem = xmlDocument.createElement("plugin");
                    Element groupIdElem = xmlDocument.createElement("groupId");
                    groupIdElem.setTextContent(dep[0]);
                    pluginElem.appendChild(groupIdElem);

                    Element artifactIdElem = xmlDocument.createElement("artifactId");
                    artifactIdElem.setTextContent(dep[1]);
                    pluginElem.appendChild(artifactIdElem);

                    Element versionElem = xmlDocument.createElement("version");
                    versionElem.setTextContent(dep[2]);
                    pluginElem.appendChild(versionElem);

                    if (dep.length > 3) {
                        Document confElem = builder.parse(new InputSource(new StringReader(dep[3])));
//                        pluginElem.appendChild( confElem);
                        copyTags(pluginElem, confElem.getDocumentElement());

                    }
                    pluginsElement.appendChild(pluginElem);
                }
            }

            updatePomFile(log, file, xmlDocument);
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException | TransformerException ex) {
            log.error(ex);
        }
    }

    private static void copyTags(Node target, Element source) {
        Document document = target.getOwnerDocument();
        String nodeName = source.getTagName();
        Element node = document.createElement(nodeName);
        NodeList childNodes = source.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    copyTags(node, (Element) child);
                    break;
                case Node.TEXT_NODE:
                    String textContent = child.getTextContent();
                    if (textContent != null && !textContent.trim().isEmpty()) {
                        node.setTextContent(textContent.trim());
                    }
            }
        }

        target.appendChild(node);

    }

    public static void addDependencies(Log log, MavenProject project, String[][] dependencies) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            XPath xPath = XPathFactory.newInstance().newXPath();
            File file = project.getFile();
            Document xmlDocument = builder.parse(file);

            log.debug("buscando dependencies");
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

            updatePomFile(log, file, xmlDocument);
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException | TransformerException ex) {
            log.error(ex);
        }
    }
}
