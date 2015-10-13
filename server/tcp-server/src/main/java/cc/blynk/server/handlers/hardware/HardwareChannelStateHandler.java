package cc.blynk.server.handlers.hardware;

import cc.blynk.server.dao.SessionDao;
import cc.blynk.server.handlers.hardware.auth.HandlerState;
import cc.blynk.server.model.DashBoard;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.widgets.others.Notification;
import cc.blynk.server.workers.notifications.NotificationsProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.common.enums.Response.DEVICE_WENT_OFFLINE;
import static cc.blynk.common.model.messages.MessageFactory.produce;
import static cc.blynk.server.utils.HandlerUtil.getState;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/20/2015.
 *
 * Removes channel from session in case it became inactive (closed from client side).
 */
@ChannelHandler.Sharable
public class HardwareChannelStateHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LogManager.getLogger(HardwareChannelStateHandler.class);

    private final SessionDao sessionDao;
    private final NotificationsProcessor notificationsProcessor;

    public HardwareChannelStateHandler(SessionDao sessionDao, NotificationsProcessor notificationsProcessor) {
        this.sessionDao = sessionDao;
        this.notificationsProcessor = notificationsProcessor;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        sessionDao.removeHardFromSession(ctx.channel());
        log.trace("Hardware channel disconnect.");
        sentOfflineMessage(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReadTimeoutException) {
            log.trace("Hardware timeout disconnect.");
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    private void sentOfflineMessage(Channel channel) {
        HandlerState handlerState = getState(channel);
        if (handlerState.user != null) {
            DashBoard dashBoard = handlerState.user.profile.getDashboardById(handlerState.dashId, 0);
            if (dashBoard.isActive) {
                Notification notification = dashBoard.getWidgetByType(Notification.class);
                if (notification == null || !notification.notifyWhenOffline) {
                    Session session = sessionDao.userSession.get(handlerState.user);
                    if (session.appChannels.size() > 0) {
                        session.sendMessageToApp(produce(0, DEVICE_WENT_OFFLINE));
                    }
                } else {
                    String boardType = dashBoard.boardType;
                    String dashName = dashBoard.name;
                    dashName = dashName == null ? "" : dashName;
                    notificationsProcessor.push(handlerState.user, notification,
                            String.format("Your %s went offline. \"%s\" project is disconnected.", boardType, dashName));
                }
            }
        }
    }


}
