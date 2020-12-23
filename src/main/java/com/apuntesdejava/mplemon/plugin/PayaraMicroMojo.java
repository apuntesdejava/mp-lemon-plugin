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
import java.util.Arrays;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Diego Silva <diego.silva at apuntesdejava.com>
 */
@Mojo(name = "add-payara-micro")
public class PayaraMicroMojo extends AbstractMojo {

    private static final String[][] MYSQL_DEPENDECIES = {
        {"mysql", "mysql-connector-java", "8.0.22"}
    };
    private static final String[][] PGSQL_DEPENDECIES = {
        {"org.postgresql", "postgresql", "42.2.18"}
    };
    private static final String CREATE_JDBC_CONNECTION_POOL = "create-jdbc-connection-pool --maxpoolsize=4 --poolresize=1 --steadypoolsize=1 --ping=true --pooling=true --restype=javax.sql.ConnectionPoolDataSource --datasourceclassname=JDBC_DRIVER --property Password=JDBC_PASSWORD:User=JDBC_USER:Url=JDBC_URL POOL_NAME";

    @Parameter(defaultValue = "5.2020.7")
    private String version;

    @Parameter(defaultValue = "/")
    private String contextRoot;

    @Parameter(defaultValue = "1.0.7")
    private String pluginVersion;

    @Parameter(property = "jdbcDriver")
    private String jdbcDriver;

    @Parameter(property = "jdbcUrl")
    private String jdbcUrl;

    @Parameter(property = "jdbcUsername")
    private String jdbcUsername;

    @Parameter(property = "jdbcPassword")
    private String jdbcPassword;

    private boolean useJdbc;
    private String createJdbcConnectionPool;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().debug("Add Payara Micro");
        MavenProject project = (MavenProject) getPluginContext().get("project");
        File baseDir = project.getBasedir();
        Artifact artifact = project.getArtifact();
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
        createScripts(baseDir);

    }

    private void createScripts(File baseDir) {
        getLog().debug("creating scripts");
        createScriptFile(baseDir, "prebootcommandfile");
        createScriptFile(baseDir, "postbootcommandfile");
        if (useJdbc) {
            createScriptFile(baseDir, "postdeploycommandfile", createJdbcConnectionPool);
        }
    }

    private void createScriptFile(File baseDir, String scriptFileName, String... contents) {
        try {
            File scriptFile = new File(baseDir, scriptFileName);
            boolean created = scriptFile.createNewFile();
            getLog().debug("File created " + created);
            if (contents.length > 0) {
                Files.write(scriptFile.toPath(), Arrays.asList(contents));
            }
        } catch (IOException ex) {
            getLog().error("error creating " + scriptFileName + " file at " + baseDir + " directory", ex);
        }
    }

    private void addJdbc(MavenProject project) {
        switch (jdbcDriver) {
            case "mysql":
                jdbcDriver = "com.mysql.cj.jdbc.MysqlConnectionPoolDataSource";
                this.createJdbcConnectionPool = CREATE_JDBC_CONNECTION_POOL
                        .replaceAll("POOL_NAME", project.getName() + "Pool")
                        .replaceAll("JDBC_URL", jdbcUrl)
                        .replaceAll("JDBC_DRIVER", jdbcDriver)
                        .replaceAll("JDBC_PASSWORD", jdbcPassword)
                        .replaceAll("JDBC_USER", jdbcUsername);
                addDependencies(getLog(), project, MYSQL_DEPENDECIES);
                break;
            case "postgresql":
                jdbcDriver = "org.postgresql.jdbc3.Jdbc3ConnectionPool";
                addDependencies(getLog(), project, PGSQL_DEPENDECIES);

        }
    }
}
