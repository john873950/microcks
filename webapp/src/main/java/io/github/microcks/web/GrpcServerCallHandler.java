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
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonEvaluationSpecification;
import io.github.microcks.util.dispatcher.JsonExpressionEvaluator;
import io.github.microcks.util.dispatcher.JsonMappingException;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.github.microcks.util.grpc.GrpcUtil;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/**
 * A Handler for GRPC Server calls invocation that is using Microcks dispatching and mock definitions.
 * @author laurent
 */
@Component
public class GrpcServerCallHandler {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GrpcServerCallHandler.class);

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private ApplicationContext applicationContext;

   /**
    * Create an ServerCallHandler that uses Microcks mocks for unary calls.
    * @param fullMethodName The GRPC method full name.
    * @return A ServerCallHandler
    */
   public ServerCallHandler<byte[], byte[]> getUnaryServerCallHandler(String fullMethodName) {
      return ServerCalls.asyncUnaryCall(new MockedUnaryMethod(fullMethodName));
   }

   /**
    * This internal class is handling UnaryMethod calls.
    * It takes care of building a JSON representation from input, apply a dispatcher to
    * find correct response and serialize response JSON content into binary back.
    */
   protected class MockedUnaryMethod implements ServerCalls.UnaryMethod<byte[], byte[]> {

      private String fullMethodName;
      private String serviceName;
      private String serviceVersion;
      private String operationName;

      /**
       * Build a UnaryMethod for handling GRPC call.
       * @param fullMethodName The GRPC method full identifier.
       */
      public MockedUnaryMethod(String fullMethodName) {
         this.fullMethodName = fullMethodName;
         // Retrieve operation name, service name and version from fullMethodName.
         operationName = fullMethodName.substring(fullMethodName.indexOf("/") + 1);
         serviceName = fullMethodName.substring(fullMethodName.lastIndexOf(".") + 1, fullMethodName.indexOf("/"));
         String packageName = fullMethodName.substring(0, fullMethodName.lastIndexOf("."));
         String[] parts = packageName.split("\\.");
         serviceVersion = (parts.length > 2 ? parts[parts.length - 1] : packageName);
      }

      @Override
      public void invoke(byte[] bytes, StreamObserver<byte[]> streamObserver) {
         log.info("Servicing mock response for service [{}, {}] and method {}",
               serviceName, serviceVersion, operationName);

         DynamicMessage outMsg = null;
         long startTime = System.currentTimeMillis();

         try {
            // Get service and spotted operation.
            Service service = serviceRepository.findByNameAndVersion(serviceName, serviceVersion);
            if (service == null) {
               // No service found.
               log.debug("No GRPC Service def found for [{}, {}]", serviceName, serviceVersion);
               streamObserver.onError(Status.UNIMPLEMENTED.withDescription("No GRPC Service def found for " + fullMethodName).asException());
               return;
            }
            Operation grpcOperation = null;
            for (Operation operation : service.getOperations()) {
               if (operation.getName().equals(operationName)) {
                  grpcOperation = operation;
                  break;
               }
            }

            if (grpcOperation != null) {
               log.debug("Found a valid operation {} with rules: {}", grpcOperation.getName(), grpcOperation.getDispatcherRules());

               // We must find dispatcher and its rules. Default to operation ones but
               // if we have a Fallback this is the one who is holding the first pass rules.
               String dispatcher = grpcOperation.getDispatcher();
               String dispatcherRules = grpcOperation.getDispatcherRules();
               FallbackSpecification fallback = MockControllerCommons.getFallbackIfAny(grpcOperation);
               if (fallback != null) {
                  dispatcher = fallback.getDispatcher();
                  dispatcherRules = fallback.getDispatcherRules();
               }

               // In order to inspect incoming byte array, we need the Protobuf binary descriptor that should
               // have been processed while importing the .proto schema for the service.
               List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.PROTOBUF_DESCRIPTOR);
               if (resources == null || resources.size() != 1) {
                  log.error("Did not found any pre-processed Protobuf binary descriptor...");
               }
               Resource pbResource = resources.get(0);

               Descriptors.MethodDescriptor md = GrpcUtil.findMethodDescriptor(pbResource.getContent(), serviceName, operationName);

               // Now parse the incoming message.
               DynamicMessage inMsg = DynamicMessage.parseFrom(md.getInputType(), bytes);
               String jsonBody = JsonFormat.printer().print(inMsg);
               log.debug("Request body: {}", jsonBody);

               //
               String dispatchCriteria = computeDispatchCriteria(dispatcher, dispatcherRules, jsonBody);
               log.debug("Dispatch criteria for finding response is {}", dispatchCriteria);

               // For now - regarding the available dispatchers - we only dealing with response names.
               List<Response> responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, grpcOperation), dispatchCriteria);
               if (responses.isEmpty() && fallback != null) {
                  // If we've found nothing and got a fallback, that's the moment!
                  responses = responseRepository.findByOperationIdAndName(IdBuilder.buildOperationId(service, grpcOperation), fallback.getFallback());
               }

               // No filter to apply, just check that we have a response.
               if (responses.size() > 0) {
                  Response response = responses.get(0);
                  // Use a builder for out type with a Json parser to merge content and build outMsg.
                  DynamicMessage.Builder outBuilder = DynamicMessage.newBuilder(md.getOutputType());

                  // Render response content before.
                  String responseContent = MockControllerCommons.renderResponseContent(jsonBody, response);

                  JsonFormat.parser().merge(responseContent, outBuilder);
                  outMsg = outBuilder.build();

                  // Setting delay to default one if not set.
                  if (grpcOperation.getDefaultDelay() != null) {
                     MockControllerCommons.waitForDelay(startTime, grpcOperation.getDefaultDelay());
                  }

                  // Publish an invocation event before returning.
                  MockControllerCommons.publishMockInvocation(applicationContext, this, service, response, startTime);

                  // Send the output message and complete the stream.
                  streamObserver.onNext(outMsg.toByteArray());
                  streamObserver.onCompleted();
               } else {
                  // No response found.
                  log.info("No appropriate response found for this input {}, returning an error", jsonBody);
                  streamObserver.onError(Status.NOT_FOUND.withDescription("No response found for the GRPC input request").asException());
               }
            } else {
               // No operation found.
               log.debug("No valid operation found for [{}, {}] and {}", serviceName, serviceVersion, operationName);
               streamObserver.onError(Status.UNIMPLEMENTED.withDescription("No valid operation found for " + fullMethodName).asException());
            }
         } catch (Throwable t) {
            log.error("Unexpected throwable during GRPC input request processing", t);
            streamObserver.onError(Status.UNKNOWN.withDescription("Unexpected throwable during GRPC input request processing").withCause(t).asException());
            t.printStackTrace();
         }
      }
   }

   /** Create a dispatchCriteria string from type, rules and request elements. */
   private String computeDispatchCriteria(String dispatcher, String dispatcherRules, String jsonBody) {
      String dispatchCriteria = null;

      // Depending on dispatcher, evaluate request with rules.
      if (dispatcher != null) {
         switch (dispatcher) {
            case DispatchStyles.JSON_BODY:
               try {
                  JsonEvaluationSpecification specification = JsonEvaluationSpecification.buildFromJsonString(dispatcherRules);
                  dispatchCriteria = JsonExpressionEvaluator.evaluate(jsonBody, specification);
               } catch (JsonMappingException jme) {
                  log.error("Dispatching rules of operation cannot be interpreted as JsonEvaluationSpecification", jme);
               }
               break;
         }
      }
      return dispatchCriteria;
   }
}
