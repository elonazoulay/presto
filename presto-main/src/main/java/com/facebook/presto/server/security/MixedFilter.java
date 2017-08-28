/*
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
package com.facebook.presto.server.security;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.airlift.log.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Enumeration;

import static javax.ws.rs.core.HttpHeaders.USER_AGENT;

public class MixedFilter
        implements Filter
{
    private static final Logger LOG = Logger.get(MixedFilter.class);

    private static final String INCLUDE_REALM_HEADER = "X-Airlift-Realm-In-Challenge";
    private static final String DAIQUERY = "flib";
    private static final String SOURCE = "X-Presto-Source";
    private static final String DATA_SWARM = "dataswarm";

    private final Filter ldapFilter;
    private final Filter krbFilter;

    @Inject
    public MixedFilter(@Named("ldap") Filter ldapFilter, @Named("kerberos") Filter krbFilter)
    {
        this.ldapFilter = ldapFilter;
        this.krbFilter = krbFilter;
    }

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
        ldapFilter.init(filterConfig);
        krbFilter.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain nextFilter)
            throws IOException, ServletException
    {
        // skip auth for http
        if (!servletRequest.isSecure()) {
            nextFilter.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        dumpRequest(request);

        if (request.getHeader(INCLUDE_REALM_HEADER) != null) {
            krbFilter.doFilter(servletRequest, servletResponse, nextFilter);
            return;
        }

        if (headerStartsWith(request, USER_AGENT, DAIQUERY)) {
            nextFilter.doFilter(servletRequest, servletResponse);
            return;
        }

        if (headerStartsWith(request, SOURCE, DATA_SWARM)) {
            krbFilter.doFilter(servletRequest, servletResponse, nextFilter);
            return;
        }

        ldapFilter.doFilter(servletRequest, servletResponse, nextFilter);
    }

    private boolean headerStartsWith(HttpServletRequest request, String headerName, String headerPrefix)
    {
        String headerValue = request.getHeader(headerName);
        return headerValue != null && headerValue.startsWith(headerPrefix);
    }

    private void dumpRequest(HttpServletRequest request)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("URI: %s\n", request.getRequestURI()));
        sb.append(String.format("METHOD: %s\n", request.getMethod()));

        Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            if (headerName.equalsIgnoreCase("Authorization")) {
                sb.append(String.format("HEADER: %s = %s\n", headerName, request.getHeader(headerName).substring(0, 5) + "******"));
            }
            else {
                sb.append(String.format("HEADER: %s = %s\n", headerName, request.getHeader(headerName)));
            }
        }

        LOG.debug("New request from %s, dumps: %s", request.getRemoteAddr(), sb.toString());
    }

    @Override
    public void destroy()
    {
        ldapFilter.destroy();
        krbFilter.destroy();
    }
}
