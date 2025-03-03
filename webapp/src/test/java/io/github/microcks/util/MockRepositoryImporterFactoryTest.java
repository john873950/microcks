/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util;

import io.github.microcks.util.asyncapi.AsyncAPIImporter;
import io.github.microcks.util.grpc.ProtobufImporter;
import io.github.microcks.util.metadata.MetadataImporter;
import io.github.microcks.util.openapi.OpenAPIImporter;
import io.github.microcks.util.postman.PostmanCollectionImporter;
import io.github.microcks.util.soapui.SoapUIProjectImporter;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
/**
 * This is a test case for MockRepositoryImporterFactory.
 * @author laurent
 */
public class MockRepositoryImporterFactoryTest {

   @Test
   public void testGetMockRepositoryImporter() {

      // Load a SoapUI file.
      File soapUIProject = new File("../samples/HelloService-soapui-project.xml");
      MockRepositoryImporter importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(soapUIProject, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof SoapUIProjectImporter);

      // Load a Postman file.
      File postmanCollection = new File("../samples/PetstoreAPI-collection.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(postmanCollection, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof PostmanCollectionImporter);

      // Load an OpenAPI YAML file.
      importer = null;
      File openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml");
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an OpenAPI JSON file.
      openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/cars-openapi.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an OpenAPI JSON oneliner file.
      openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/openapi-oneliner.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an AsyncAPI JSON file.
      File asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof AsyncAPIImporter);

      // Load an AsyncAPI JSON oneliner file.
      asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-oneliner.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof AsyncAPIImporter);

      // Load an AsyncAPI YAML file.
      asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof AsyncAPIImporter);

      // Load a Protobuf schema file.
      File protobufSchema = new File("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(protobufSchema, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof ProtobufImporter);

      // Load an APIMetadata file.
      File apiMetadata = new File("target/test-classes/io/github/microcks/util/metadata/hello-grpc-v1-metadata.yml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(apiMetadata, null);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof MetadataImporter);
   }
}
