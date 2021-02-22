/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.rest;

import com.here.xyz.hub.auth.*;
import com.here.xyz.hub.connectors.models.Connector;
import com.here.xyz.hub.task.ConnectorHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.here.xyz.hub.rest.Api.HeaderValues.APPLICATION_JSON;
import static com.here.xyz.hub.rest.ApiParam.Query.OWNER;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class ConnectorApi extends Api {
  private static final Logger logger = LogManager.getLogger();

  public ConnectorApi(OpenAPI3RouterFactory routerFactory) {
    routerFactory.addHandlerByOperationId("getConnectors", this::getConnectors);
    routerFactory.addHandlerByOperationId("postConnector", this::createConnector);
    routerFactory.addHandlerByOperationId("getConnector", this::getConnector);
    routerFactory.addHandlerByOperationId("putConnector", this::replaceConnector);
    routerFactory.addHandlerByOperationId("patchConnector", this::updateConnector);
    routerFactory.addHandlerByOperationId("deleteConnector", this::deleteConnector);
  }

  private void getConnector(final RoutingContext context) {

    String connectorId = context.pathParam(ApiParam.Path.CONNECTOR_ID);

    ConnectorAuthorization.authorizeManageConnectorsRights(context, connectorId, arAuth -> {
      if (arAuth.failed()) {
        sendErrorResponse(context, arAuth.cause());
        return;
      }

      ConnectorHandler.getConnector(context, connectorId, ar -> {
            if (ar.failed()) {
              this.sendErrorResponse(context, ar.cause());
            } else {
              sendResponse(context, OK, ar.result());
            }
          }
      );
    });
  }

  private void getConnectors(final RoutingContext context) {
    List<String> queryIds = context.queryParam("id");
    Handler<AsyncResult<Void>> handler = event -> {
      if (event.failed()) {
        sendErrorResponse(context, event.cause());
        return;
      }

      if (queryIds.isEmpty()) {
        ConnectorHandler.getConnectors(context, Context.getJWT(context).aid, ar -> {
              if (ar.failed()) {
                sendErrorResponse(context, ar.cause());
              } else {
                sendResponse(context, OK, ar.result());
              }
            }
        );
      } else {
        ConnectorHandler.getConnectors(context, queryIds, ar -> {
              if (ar.failed()) {
                sendErrorResponse(context, ar.cause());
              } else {
                sendResponse(context, OK, ar.result());
              }
            }
        );
      }
    };

    if (queryIds.isEmpty()) {
      try {
        ConnectorAuthorization.authorizeManageConnectorsRights(context);
        handler.handle(Future.succeededFuture());
      } catch (HttpException e) {
        sendErrorResponse(context, e);
      }
    } else {
      ConnectorAuthorization.authorizeManageConnectorsRights(context, queryIds, handler);
    }
  }

  private void createConnector(final RoutingContext context) {
    JsonObject input;
    try {
      input = context.getBodyAsJson();
    } catch (DecodeException e) {
      context.fail(new HttpException(BAD_REQUEST, "Invalid JSON string"));
      return;
    }

    String connectorId = input.getString("id");
    if (connectorId == null) {
      sendErrorResponse(context, new HttpException(BAD_REQUEST, "Parameter 'id' for the resource is missing."));
      return;
    }

    ConnectorAuthorization.authorizeManageConnectorsRights(context, connectorId, arAuth -> {
      if (arAuth.failed()) {
        sendErrorResponse(context, arAuth.cause());
        return;
      }

      ConnectorHandler.createConnector(context, input, ar -> {
        if (ar.failed()) {
          this.sendErrorResponse(context, ar.cause());
        } else {
          sendResponse(context, CREATED, ar.result());
        }
      });
    });
  }

  private void replaceConnector(final RoutingContext context) {
    String connectorId = context.pathParam(ApiParam.Path.CONNECTOR_ID);
    ConnectorAuthorization.authorizeManageConnectorsRights(context, connectorId, arAuth -> {
      if (arAuth.failed()) {
        sendErrorResponse(context, arAuth.cause());
        return;
      }

      JsonObject input;
      try {
        input = context.getBodyAsJson();
      } catch (DecodeException e) {
        context.fail(new HttpException(BAD_REQUEST, "Invalid JSON string"));
        return;
      }

      if (input.getString("id") == null) {
        input.put("id", connectorId);
      } else if (!input.getString("id").equals(connectorId)) {
        sendErrorResponse(context, new HttpException(BAD_REQUEST, "Path ID does not match resource ID in body."));
        return;
      }

      ConnectorHandler.replaceConnector(context, input, ar -> {
        if (ar.failed()) {
          this.sendErrorResponse(context, ar.cause());
        } else {
          sendResponse(context, OK, ar.result());
        }
      });
    });
  }

  private void updateConnector(final RoutingContext context) {
    String connectorId = context.pathParam(ApiParam.Path.CONNECTOR_ID);
    ConnectorAuthorization.authorizeManageConnectorsRights(context, connectorId, arAuth -> {
      if (arAuth.failed()) {
        sendErrorResponse(context, arAuth.cause());
        return;
      }

      JsonObject input;
      try {
        input = context.getBodyAsJson();
      } catch (DecodeException e) {
        context.fail(new HttpException(BAD_REQUEST, "Invalid JSON string"));
        return;
      }

      if (input.getString("id") == null) {
        input.put("id", connectorId);
      } else if (!input.getString("id").equals(connectorId)) {
        sendErrorResponse(context, new HttpException(BAD_REQUEST, "Path ID does not match resource ID in body."));
        return;
      }

      ConnectorHandler.updateConnector(context, input, ar -> {
        if (ar.failed()) {
          this.sendErrorResponse(context, ar.cause());
        } else {
          sendResponse(context, OK, ar.result());
        }
      });
    });
  }

  private void deleteConnector(final RoutingContext context) {
    String connectorId = context.pathParam(ApiParam.Path.CONNECTOR_ID);
    ConnectorAuthorization.authorizeManageConnectorsRights(context, connectorId, arAuth -> {
      if (arAuth.failed()) {
        sendErrorResponse(context, arAuth.cause());
        return;
      }

      ConnectorHandler.deleteConnector(context, connectorId, ar -> {
        if (ar.failed()) {
          this.sendErrorResponse(context, ar.cause());
        } else {
          sendResponse(context, OK, ar.result());
        }
      });
    });
  }

  private void sendResponse(RoutingContext context, HttpResponseStatus status, Object o) {
    HttpServerResponse httpResponse = context.response().setStatusCode(status.code());

    byte[] response;
    try {
      response = Json.encode(o).getBytes(StandardCharsets.UTF_8);
    } catch (EncodeException e) {
      sendErrorResponse(context, new HttpException(INTERNAL_SERVER_ERROR, "Could not serialize response.", e));
      return;
    }

    if (response == null || response.length == 0) {
      httpResponse.setStatusCode(NO_CONTENT.code()).end();
    } else if (response.length > getMaxResponseLength(context)) {
      sendErrorResponse(context, new HttpException(RESPONSE_PAYLOAD_TOO_LARGE, RESPONSE_PAYLOAD_TOO_LARGE_MESSAGE));
    } else {
      httpResponse.putHeader(CONTENT_TYPE, APPLICATION_JSON);
      httpResponse.end(Buffer.buffer(response));
    }
  }

  private void sendErrorResponse(RoutingContext context, Throwable throwable) {
    HttpException e;
    if (throwable instanceof HttpException) {
      e = (HttpException) throwable;
    } else {
      e = new HttpException(INTERNAL_SERVER_ERROR, throwable.getMessage(), throwable);
    }
    sendErrorResponse(context, e);
  }


  private static class ConnectorAuthorization extends Authorization {
    public static void authorizeManageConnectorsRights(RoutingContext context, String connectorId, Handler<AsyncResult<Void>> handler) {
      authorizeManageConnectorsRights(context, Arrays.asList(connectorId), handler);
    }

    public static void authorizeManageConnectorsRights(RoutingContext context, List<String> connectorIds, Handler<AsyncResult<Void>> handler) {
      final XyzHubActionMatrix requestRights = new XyzHubActionMatrix();
      List<CompletableFuture<Void>> futureList = new ArrayList<>();
      connectorIds.forEach(connectorId ->
          futureList.add(checkConnector(context, requestRights, connectorId))
      );

      CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()]))
          .thenRun(() -> {
            try {
              evaluateRights(Context.getMarker(context), requestRights, Context.getJWT(context).getXyzHubMatrix());
              handler.handle(Future.succeededFuture());
            } catch (HttpException e) {
              handler.handle(Future.failedFuture(e));
            }
          });
    }

    public static void authorizeManageConnectorsRights(RoutingContext context) throws HttpException {
      JWTPayload jwt = Context.getJWT(context);

      final XyzHubActionMatrix requestRights = new XyzHubActionMatrix();
      requestRights.manageConnectors(new XyzHubAttributeMap().withValue(OWNER, jwt.aid));

      evaluateRights(Context.getMarker(context), requestRights, jwt.getXyzHubMatrix());
    }

    private static CompletableFuture<Void> checkConnector(RoutingContext context, XyzHubActionMatrix requestRights, String connectorId) {
      CompletableFuture<Void> f = new CompletableFuture<>();
      ConnectorHandler.getConnector(context, connectorId, ar -> {
        if (ar.succeeded()) {
          Connector c = ar.result();
          if (c.owner != null)
            requestRights.manageConnectors(XyzHubAttributeMap.forIdValues(c.owner, c.id));
          else
            requestRights.manageConnectors(XyzHubAttributeMap.forIdValues(c.id));
          f.complete(null);
        } else {
          //If connector does not exist.
          requestRights.manageConnectors(XyzHubAttributeMap.forIdValues(Context.getJWT(context).aid, connectorId));
          f.complete(null);
        }
      });
      return f;
    }
  }
}