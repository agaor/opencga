/*
 * Copyright 2015 OpenCB
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
 */

package org.opencb.opencga.storage.core.config;

import java.util.List;

/**
 * Created by imedina on 07/09/15.
 */
public class QueryServerConfiguration {

    private int port;
    private List<String> authorizedHosts;

    public QueryServerConfiguration() {
    }

    public QueryServerConfiguration(int port, List<String> authorizedHosts) {
        this.port = port;
        this.authorizedHosts = authorizedHosts;
    }

    @Override
    public String toString() {
        return "QueryServerConfiguration{" +
                "port=" + port +
                ", authorizedHosts=" + authorizedHosts +
                '}';
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<String> getAuthorizedHosts() {
        return authorizedHosts;
    }

    public void setAuthorizedHosts(List<String> authorizedHosts) {
        this.authorizedHosts = authorizedHosts;
    }

}
