package cc.blynk.utils;

import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import io.netty.channel.Channel;

/**
 * Used instead of Netty's DefaultAttributeMap as it faster and
 * doesn't involves any synchronization at all.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 13.09.15.
 */
public class StateHolderUtil {

    public static HardwareStateHolder getHardState(Channel channel) {
        BaseSimpleChannelInboundHandler handler = channel.pipeline().get(BaseSimpleChannelInboundHandler.class);
        return handler == null ? null : (HardwareStateHolder) handler.getState();
    }

    public static boolean isSameDash(Channel channel, int dashId) {
        BaseSimpleChannelInboundHandler handler = channel.pipeline().get(BaseSimpleChannelInboundHandler.class);
        return ((HardwareStateHolder) handler.getState()).dash.id == dashId;
    }

    public static boolean isSameDashAndDeviceId(Channel channel, int dashId, int deviceId) {
        BaseSimpleChannelInboundHandler handler = channel.pipeline().get(BaseSimpleChannelInboundHandler.class);
        if (handler == null) {
            return false;
        }
        HardwareStateHolder hardwareStateHolder = (HardwareStateHolder) handler.getState();
        return hardwareStateHolder.dash.id == dashId && hardwareStateHolder.device.id == deviceId;
    }

}
