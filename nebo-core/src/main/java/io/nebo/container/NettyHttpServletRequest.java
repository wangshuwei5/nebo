/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.nebo.container;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.HttpRequest;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;

/**
 * {@link javax.servlet.http.HttpServletRequest} wrapper for Netty's {@link io.netty.handler.codec.http.HttpRequest}.
 *
 * @author Danny Thomas
 */
public  class NettyHttpServletRequest implements HttpServletRequest {
    public static final String DISPATCHER_TYPE = NettyRequestDispatcher.class.getName() + ".DISPATCHER_TYPE";

    private final ChannelHandlerContext ctx;
    private final NettyEmbeddedContext servletContext;
    private final HttpRequest request;
    private final ServletInputStream inputStream;
    private  Map<String, Object> attributes;
    private boolean asyncSupported = true;
    private NettyAsyncContext asyncContext;
    private HttpServletResponse servletResponse;
    private Map<String, String[]> params;
    private InetSocketAddress insocket;
    private String requestURI;
    private String queryString;
    NettyHttpServletRequest(ChannelHandlerContext ctx, NettyEmbeddedContext servletContext, HttpRequest request,ServletInputStream inputStream,HttpServletResponse servletResponse) {
        this.ctx = ctx;
        this.servletContext = servletContext;
        this.request = request;
        this.inputStream = inputStream;
        this.attributes = new HashMap<>();
        this.insocket = (InetSocketAddress) ctx.channel().remoteAddress();
        this.servletResponse = servletResponse;
        this.params = new ConcurrentHashMap<String,String[]>();
        this.attributes = new ConcurrentHashMap<String, Object>();
        String uri = request.getUri();

        if(uri.indexOf("?") != -1){
            String[] strs = uri.split("\\?");
            this.requestURI = strs[0];
            this.queryString = strs[1];
        }else {
            this.requestURI = uri;
        }

        if(queryString!=null){
            String[] params = queryString.split("&");
            for(String paramStr : params){
                String[] paramArr = paramStr.split("=");
                String key = paramArr[0];
                String value = paramArr[1];
                HttpRequestUtils.setParamMap(key, value, this.params);
            }
        }
    }


    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        String cookieString = this.request.headers().get(COOKIE);
        if (cookieString != null) {
            Set<io.netty.handler.codec.http.Cookie> cookies = CookieDecoder.decode(cookieString);
            if (!cookies.isEmpty()) {
                Cookie[] cookiesArray = new Cookie[cookies.size()];
                int index = 0;
                for (io.netty.handler.codec.http.Cookie c : cookies) {
                    Cookie cookie = new Cookie(c.getName(), c.getValue());
                    cookie.setComment(c.getComment());
                    if(c.getDomain()!=null)cookie.setDomain(c.getDomain());
                    cookie.setMaxAge((int) c.getMaxAge());
                    cookie.setPath(c.getPath());
                    cookie.setSecure(c.isSecure());
                    cookie.setVersion(c.getVersion());
                    cookiesArray[index] = cookie;
                    index++;
                }
                return cookiesArray;

            }
        }
        return new Cookie[0];
    }

    @Override
    public long getDateHeader(String name) {
        return 0;
    }

    @Override
    public String getHeader(String name) {
        return null;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.emptyEnumeration();
    }

    @Override
    public int getIntHeader(String name) {
        return 0;
    }

    @Override
    public String getMethod() {
        return request.method().name();
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getContextPath() {
        String requestURI = getRequestURI();
        return "/".equals(requestURI) ? "" : requestURI;
    }

    @Override
    public String getQueryString() {
        return this.queryString;
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getRequestURI() {
        return this.requestURI;
    }

    @Override
    public StringBuffer getRequestURL() {
        return new StringBuffer(this.requestURI);
    }

    @Override
    public String getServletPath() {
        return getRequestURI();
    }

    @Override
    public HttpSession getSession(boolean create) {
        return ServletNettyHttpSessionManager.getSession(this, create);
    }

    @Override
    public HttpSession getSession() {
        return ServletNettyHttpSessionManager.getSession(this, true);
    }

    @Override
    public String changeSessionId() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {

    }

    @Override
    public void logout() throws ServletException {

    }

    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException {
        return null;
    }

    @Override
    public Part getPart(String name) throws IOException, IllegalStateException, ServletException {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        synchronized (attributes) {
            return attributes.get(name);
        }
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        synchronized (attributes) {
            return Collections.enumeration(attributes.keySet());
        }
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {

    }

    @Override
    public int getContentLength() {
        return 0;
    }

    @Override
    public long getContentLengthLong() {
        return 0;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        return values != null ? values[0] : null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return null;
    }

    @Override
    public String[] getParameterValues(String name) {
       return params.get(name);
    }

    @Override
    public Map getParameterMap() {
        return this.params;
    }

    @Override
    public String getProtocol() {
        return request.protocolVersion().protocolName();
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public int getServerPort() {
        return 0;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        return null;
    }

    @Override
    public String getRemoteHost() {
        return this.insocket.getAddress().getHostAddress();
    }

    @Override
    public void setAttribute(String name, Object o) {
        synchronized (attributes) {
            attributes.put(name, o);
        }
    }

    @Override
    public void removeAttribute(String name) {
        synchronized (attributes) {
            attributes.remove(name);
        }
    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return null;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public String getRealPath(String path) {
        return null;
    }

    @Override
    public int getRemotePort() {
        return 0;
    }

    @Override
    public String getLocalName() {
        return null;
    }

    @Override
    public String getLocalAddr() {
        return this.insocket.getHostString();
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public AsyncContext startAsync() {
        return startAsync(this, null);
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        return ((NettyAsyncContext) getAsyncContext()).startAsync(servletRequest, servletResponse);
    }

    @Override
    public boolean isAsyncStarted() {
        return null != asyncContext && asyncContext.isAsyncStarted();
    }

    void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    @Override
    public AsyncContext getAsyncContext() {
        if (null == asyncContext) {
            asyncContext = new NettyAsyncContext(this, ctx);
        }
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return attributes.containsKey(DISPATCHER_TYPE) ? (DispatcherType) attributes.get(DISPATCHER_TYPE) : DispatcherType.REQUEST;
    }

    public ServletResponse getServletResponse() {
        return servletResponse;
    }

    public HttpRequest getNettyRequest() {
        return request;
    }


    public String getCookie(String key) {
        Cookie[] cookies = this.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies){
                if(key.equals(cookie.getName())){
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
