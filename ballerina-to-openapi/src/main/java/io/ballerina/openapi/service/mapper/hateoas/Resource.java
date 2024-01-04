/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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

package io.ballerina.openapi.service.mapper.hateoas;

public class Resource {
    private final String resourceMethod;
    private final String operationId;

    public Resource(String resourceName, String resourceMethod, String operationId) {
        this.resourceMethod = resourceMethod;
        this.operationId = operationId;
    }

    public String getResourceMethod() {
        return resourceMethod;
    }

    public String getOperationId() {
        return operationId;
    }
}
