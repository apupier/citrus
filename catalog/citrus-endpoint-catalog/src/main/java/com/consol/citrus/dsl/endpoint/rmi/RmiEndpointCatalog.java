/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.dsl.endpoint.rmi;

import com.consol.citrus.rmi.client.RmiClientBuilder;
import com.consol.citrus.rmi.endpoint.builder.RmiEndpoints;
import com.consol.citrus.rmi.server.RmiServerBuilder;

/**
 * @author Christoph Deppisch
 */
public class RmiEndpointCatalog {

    /**
     * Private constructor setting the client and server builder implementation.
     */
    private RmiEndpointCatalog() {
        // prevent direct instantiation
    }

    public static RmiEndpointCatalog rmi() {
        return new RmiEndpointCatalog();
    }

    /**
     * Gets the client builder.
     * @return
     */
    public RmiClientBuilder client() {
        return RmiEndpoints.rmi().client();
    }

    /**
     * Gets the client builder.
     * @return
     */
    public RmiServerBuilder server() {
        return RmiEndpoints.rmi().server();
    }
}
