/*
 * Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.test.resources;

import org.ballerinalang.test.BaseTest;
import org.ballerinalang.test.context.BMainInstance;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.LogLeecher;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.jar.JarFile;

import static org.ballerinalang.test.packaging.PackerinaTestUtils.deleteFiles;

/**
 * Test cases for accessing resources scenarios.
 */
public class ResourcesTestCase extends BaseTest {
    private BMainInstance balClient;
    private String basePath;

    @BeforeClass()
    public void setUp() throws BallerinaTestException {
        basePath = new File(Paths.get("src", "test", "resources", "resources").toString()).getAbsolutePath();
        balClient = new BMainInstance(balServer);
    }

    @Test(description = "Test building the ResourceProject")
    public void testResourceModuleBuild() throws BallerinaTestException {
        String moduleBuildMessage = "target/bin/resourceAPI.jar";
        LogLeecher moduleBuildLeecher = new LogLeecher(moduleBuildMessage);

        balClient.runMain("build", new String[]{"-a"}, new HashMap<>(), new String[]{},
                new LogLeecher[]{moduleBuildLeecher}, basePath);
        moduleBuildLeecher.waitForText(5000);
    }

    @Test(description = "Test resources and imported resources added to the module jar",
            dependsOnMethods = "testResourceModuleBuild")
    public void testResourceAdditionToModuleJar() throws IOException {
        Path executableJarPath = Paths.get(basePath.concat("/target/bin/resourceAPI.jar"));
        Assert.assertTrue(Files.exists(executableJarPath));

        JarFile jar = new JarFile(executableJarPath.toFile());
        // check module resources
        Assert.assertNotNull(jar.getJarEntry("resources/ballerina-test/resourceAPI/0.1.0/abc/pqr/resource.txt"));
        // check imported module resources
        Assert.assertNotNull(jar.getJarEntry("resources/praveenn/testResource/0.1.0/abc1/download.jpg"));
    }

    @Test(description = "Test resources and imported resources added to the testable jar",
            dependsOnMethods = "testResourceAdditionToModuleJar")
    public void testResourceAdditionToTestableJar() throws IOException {
        String testableJar = "/target/caches/jar_cache/ballerina-test/resourceAPI/0.1.0" +
                "/ballerina-test-resourceAPI-0.1.0-testable.jar";
        Path testableJarPath = Paths.get(basePath.concat(testableJar));
        Assert.assertTrue(Files.exists(testableJarPath));

        JarFile jar = new JarFile(testableJarPath.toFile());
        // check module's tests resources
        Assert.assertNotNull(jar.getJarEntry("resources/ballerina-test/resourceAPI/0.1.0/xyz/resource.txt"));
        // check imported module's tests resources
        Assert.assertNotNull(jar.getJarEntry("resources/ballerinax/aws.s3/0.11.0/.keep"));
        // check module's resources in testable jar
        Assert.assertNotNull(jar.getJarEntry("resources/praveenn/testResource/0.1.0/abc1/download.jpg"));
    }

    @Test(description = "Test running the ResourceProject", dependsOnMethods = "testResourceAdditionToTestableJar")
    public void testResourceModuleRun() throws BallerinaTestException {
        // Run and see the output
        String resourceFileMessage = "Hello Ballerina!!!";
        String resourceDirectoryMessage = "abc/pqr/resource.txt";
        String resourceDirectoryMessage2 = "resource.jpg";
        LogLeecher resourceFileLeecher = new LogLeecher(resourceFileMessage);
        LogLeecher resourceDirectoryLeecher = new LogLeecher(resourceDirectoryMessage);
        LogLeecher resourceDirectoryLeecher2 = new LogLeecher(resourceDirectoryMessage2);
        balClient.runMain("run", new String[] { "target/bin/resourceAPI.jar" }, new HashMap<>(), new String[0],
                new LogLeecher[] { resourceFileLeecher, resourceDirectoryLeecher, resourceDirectoryLeecher2}, basePath);
        resourceFileLeecher.waitForText(10000);
        resourceDirectoryLeecher.waitForText(10000);
        resourceDirectoryLeecher2.waitForText(10000);
    }

    @AfterClass
    private void cleanup() throws Exception {
        deleteFiles(Paths.get(this.basePath, "target").toAbsolutePath());
    }
}
