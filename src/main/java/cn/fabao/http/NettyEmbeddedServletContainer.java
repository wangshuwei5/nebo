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

package cn.fabao.http;

import cn.fabao.DispatcherInbound;
import cn.fabao.thrift.ThriftEndpoint;
import com.google.common.base.StandardSystemProperty;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;

/**
 * An {@link org.springframework.boot.context.embedded.EmbeddedServletContainer} used to control an embedded Netty instance, that bridges to
 * {@link javax.servlet.http.HttpServletRequest} and from {@link javax.servlet.http.HttpServletResponse}
 * to Netty HTTP codec {@link io.netty.handler.codec.http.HttpMessage}s.
 * <p/>
 * This is a minimal Servlet 3.1 implementation to provide for the opinionated embedded servlet container model for
 * Spring Boot, supporting a single context, runtime {@link javax.servlet.Registration} only, and no default or JSP
 * servlets.
 * <p/>
 * This class should be created using the {@link NettyEmbeddedServletContainerFactory}.
 *
 * @author Danny Thomas
 */
public class NettyEmbeddedServletContainer implements EmbeddedServletContainer {
    private final Log logger = LogFactory.getLog(getClass());
    private final InetSocketAddress address;
    private final NettyEmbeddedContext context;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private DefaultEventExecutorGroup servletExecutor;

    public NettyEmbeddedServletContainer(InetSocketAddress address, NettyEmbeddedContext context) {
        this.address = address;
        this.context = context;
    }

    @Override
    public void start() throws EmbeddedServletContainerException {
        ServerBootstrap b = new ServerBootstrap();
        groups(b);
//        servletExecutor = new DefaultEventExecutorGroup(50);
//        b.childHandler(new NettyEmbeddedServletInitializer(servletExecutor, context));
        b.childHandler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new DispatcherInbound(address, context));
            }
        });
        // Don't yet need the complexity of lifecycle state, listeners etc, so tell the context it's initialised here


        ChannelFuture future = b.bind(address).awaitUninterruptibly();
        //noinspection ThrowableResultOfMethodCallIgnored
        Throwable cause = future.cause();
        if (null != cause) {
            throw new EmbeddedServletContainerException("Could not start Netty server", cause);
        }
        logger.info(context.getServerInfo() + " started on port: " + getPort());
        context.setInitialised(true);
        ServletNettyHttpSessionManager.start();
        registerSrv();
    }

    private void groups(ServerBootstrap b) {
        if (StandardSystemProperty.OS_NAME.value().equals("Linux")) {
            bossGroup = new EpollEventLoopGroup(1);
            workerGroup = new EpollEventLoopGroup();
            b.channel(EpollServerSocketChannel.class)
                    .group(bossGroup, workerGroup)
                    .option(EpollChannelOption.TCP_CORK, true);
        } else {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            b.channel(NioServerSocketChannel.class)
                    .group(bossGroup, workerGroup);
        }
        b.option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_BACKLOG, 100);
        logger.info("Bootstrap configuration: " + b.toString());
    }

    @Override
    public void stop() throws EmbeddedServletContainerException {
        try {
            if (null != bossGroup) {
                bossGroup.shutdownGracefully().await();
            }
            if (null != workerGroup) {
                workerGroup.shutdownGracefully().await();
            }
            if (null != servletExecutor) {
                servletExecutor.shutdownGracefully().await();
            }
        } catch (InterruptedException e) {
            throw new EmbeddedServletContainerException("Container stop interrupted", e);
        }
    }

    @Override
    public int getPort() {
        return address.getPort();
    }



    private void registerSrv() {
        TMultiplexedProcessor tProcessor = new TMultiplexedProcessor();
        context.setProcessor(tProcessor);
        WebApplicationContext webApplicationContext = WebApplicationContextUtils.findWebApplicationContext(context);
        String[] strarr = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(webApplicationContext,Object.class);
        for (String s :strarr){
            Object target = webApplicationContext.getBean(s);
            ThriftEndpoint thriftEndpointAnnotation = target.getClass().getAnnotation(ThriftEndpoint.class);
            if(thriftEndpointAnnotation!=null){
                try {
                    Class targetInterface = target.getClass().getInterfaces()[0];
                    Class processorClass = Class.forName(targetInterface.getName().split("\\$")[0] + "$Processor");
                    TProcessor p = (TProcessor) processorClass.getDeclaredConstructors()[0].newInstance(target);
                    if(StringUtils.isNotBlank(thriftEndpointAnnotation.serviceName())){
                        s = thriftEndpointAnnotation.serviceName();
                    }
                    System.out.println(thriftEndpointAnnotation.serviceName());
                    logger.info("registerProcessorName : " + s + " registerProcessorClass: " + p.getClass());
                    tProcessor.registerProcessor(s,p);
                } catch (Exception e) {
                    logger.error("registerProcessor error : " + e.getMessage() , e);
                }
            }

        }
    }




}
