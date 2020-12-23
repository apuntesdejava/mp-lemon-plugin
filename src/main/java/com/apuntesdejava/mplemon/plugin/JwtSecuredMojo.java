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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Diego Silva <diego.silva at apuntesdejava.com>
 */
@Mojo(name = "jwtsecured")
public class JwtSecuredMojo extends AbstractMojo {

    @Parameter(
            property = "jwt-config",
            defaultValue = "../jwt-config.json"
    )
    private String jwtConfigJsonFile;

    private List<String> roles = new ArrayList<>();
    private String issuer;
    private int tokenValid;
    private String headerKey;
    private String publicKey;
    private String privateKey;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            MavenProject project = (MavenProject) getPluginContext().get("project");

            loadJwtConfig();
            createMPconfig(project);
        } catch (FileNotFoundException ex) {
            getLog().error(jwtConfigJsonFile + " file not found.", ex);
        } catch (IOException ex) {
            getLog().error(ex.getMessage(), ex);
        }

    }

    private void loadJwtConfig() throws FileNotFoundException, IOException {
        getLog().debug("** Loading json");
        List<String> lines = Files.readAllLines(Paths.get(jwtConfigJsonFile));
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line);
        }
        JSONObject config = new JSONObject(sb.toString());
        JSONArray rolesJson = config.getJSONArray("roles");
        for (Object object : rolesJson.toList()) {
            roles.add((String) object);
        }
        this.issuer = config.getString("issuer");
        this.tokenValid = config.getInt("token-valid");
        this.headerKey = config.getString("header-key");
        this.publicKey = config.getString("public-key");
        this.privateKey = config.getString("private-key");
    }

    private void createMPconfig(org.apache.maven.project.MavenProject project) throws IOException {
        getLog().debug("** creating microprofile-config.properties **");
        File baseDir = project.getBasedir();
        Path mpconfig = Paths.get(baseDir.getPath(), "src", "main", "resources", "META-INF", "microprofile-config.properties");
        Files.createDirectories(mpconfig.getParent());
        List<String> props = new ArrayList<>();
        props.add("mp.jwt.verify.publickey=" + publicKey);
        props.add("mp.jwt.verify.issuer=" + issuer);
        Files.write(mpconfig, props);
    }
    /*
    mp.jwt.verify.publickey=${publicKey}
mp.jwt.verify.issuer=${issuer}

     */
}
