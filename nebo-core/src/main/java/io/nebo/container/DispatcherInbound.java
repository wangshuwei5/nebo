package io.nebo.container;



import io.nebo.protocol.ProtocolRouter;
import io.nebo.protocol.ProtocolRouterFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Created by pengbo on 2016/6/28.
 */
public class DispatcherInbound extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DispatcherInbound.class);
    private final InetSocketAddress address;
    private final NettyEmbeddedContext context;
    private final RequestDispatcherHandler requestDispatcherHandler;
    private List<ProtocolRouter> protocolRouterList = ProtocolRouterFactory.loadAllProtocolRouter();

    public DispatcherInbound(InetSocketAddress address, NettyEmbeddedContext context) {
        this.address = address;
        this.context = context;
        this.requestDispatcherHandler = new RequestDispatcherHandler(context);
        for(ProtocolRouter router : protocolRouterList){
           router.init(context);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buffer = (ByteBuf) msg;
        final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
        final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
        if(isHttp(magic1,magic2)) {
            switchToHttp(ctx);
            ctx.fireChannelRead(msg);
        }else {
            //获取其他协议路由
            for(ProtocolRouter router : protocolRouterList){
                if(router.isProtocol(buffer)){
                    router.setRounter(ctx,context);
                    ctx.fireChannelRead(msg);
                }
            }
        }
    }

    private void switchToHttp(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        p.addLast("codec", new HttpServerCodec(4096, 8192, 8192, false));
        p.addLast(new HttpObjectAggregator(65536));
        p.addLast(new ChunkedWriteHandler());
        p.addLast("servletInput", new ServletContentHandler(context,ctx.channel()));
        p.addLast(new DefaultEventExecutorGroup(200),requestDispatcherHandler);
        p.remove(this);
    }


    private static boolean isHttp(int magic1, int magic2) {
        return
                magic1 == 'G' && magic2 == 'E' || // GET
                        magic1 == 'P' && magic2 == 'O' || // POST
                        magic1 == 'P' && magic2 == 'U' || // PUT
                        magic1 == 'H' && magic2 == 'E' || // HEAD
                        magic1 == 'O' && magic2 == 'P' || // OPTIONS
                        magic1 == 'P' && magic2 == 'A' || // PATCH
                        magic1 == 'D' && magic2 == 'E' || // DELETE
                        magic1 == 'T' && magic2 == 'R' || // TRACE
                        magic1 == 'C' && magic2 == 'O';   // CONNECT
    }




}
