package de.dytanic.cloudnet.ext.syncproxy.proxprox.listener;

import de.dytanic.cloudnet.driver.event.EventListener;
import de.dytanic.cloudnet.driver.event.events.channel.ChannelMessageReceiveEvent;
import de.dytanic.cloudnet.driver.event.events.service.CloudServiceDisconnectNetworkEvent;
import de.dytanic.cloudnet.driver.event.events.service.CloudServiceInfoUpdateEvent;
import de.dytanic.cloudnet.driver.event.events.service.CloudServiceStopEvent;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyConfiguration;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyConfigurationProvider;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyConstants;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyProxyLoginConfiguration;
import de.dytanic.cloudnet.ext.syncproxy.proxprox.ProxProxCloudNetSyncProxyPlugin;
import de.dytanic.cloudnet.wrapper.event.service.ServiceInfoSnapshotConfigureEvent;
import io.gomint.proxprox.api.ChatColor;
import io.gomint.proxprox.api.entity.Player;

public final class ProxProxSyncProxyCloudNetListener {

    @EventListener
    public void handle(ServiceInfoSnapshotConfigureEvent event)
    {
        if (ProxProxCloudNetSyncProxyPlugin.getInstance().getProxyLoginConfiguration() != null)
            event.getServiceInfoSnapshot().getProperties().append(
                SyncProxyConstants.SYNC_PROXY_SERVICE_INFO_SNAPSHOT_ONLINE_COUNT,
                ProxProxCloudNetSyncProxyPlugin.getProxyServer().getPlayers().size()
            );
    }

    @EventListener
    public void handle(CloudServiceInfoUpdateEvent event)
    {
        if (!event.getServiceInfo().getServiceId().getEnvironment().isMinecraftJavaProxy() &&
            !event.getServiceInfo().getServiceId().getEnvironment().isMinecraftBedrockProxy()) return;

        SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration = ProxProxCloudNetSyncProxyPlugin.getInstance().getProxyLoginConfiguration();

        if (syncProxyProxyLoginConfiguration != null)
            if (ProxProxCloudNetSyncProxyPlugin.getInstance().inGroup(event.getServiceInfo(), syncProxyProxyLoginConfiguration) &&
                event.getServiceInfo().getProperties().contains(SyncProxyConstants.SYNC_PROXY_SERVICE_INFO_SNAPSHOT_ONLINE_COUNT))
                ProxProxCloudNetSyncProxyPlugin.getInstance().getOnlineCountOfProxies().put(event.getServiceInfo().getServiceId().getUniqueId(), event.getServiceInfo()
                    .getProperties().getInt(SyncProxyConstants.SYNC_PROXY_SERVICE_INFO_SNAPSHOT_ONLINE_COUNT));
    }

    @EventListener
    public void handle(CloudServiceStopEvent event)
    {
        if (!event.getServiceInfo().getServiceId().getEnvironment().isMinecraftJavaProxy() &&
            !event.getServiceInfo().getServiceId().getEnvironment().isMinecraftBedrockProxy()) return;

        ProxProxCloudNetSyncProxyPlugin.getInstance().getOnlineCountOfProxies().remove(event.getServiceInfo().getServiceId().getUniqueId());
    }

    @EventListener
    public void handle(CloudServiceDisconnectNetworkEvent event)
    {
        if (!event.getServiceInfo().getServiceId().getEnvironment().isMinecraftJavaProxy() &&
            !event.getServiceInfo().getServiceId().getEnvironment().isMinecraftBedrockProxy()) return;

        ProxProxCloudNetSyncProxyPlugin.getInstance().getOnlineCountOfProxies().remove(event.getServiceInfo().getServiceId().getUniqueId());
    }

    @EventListener
    public void handle(ChannelMessageReceiveEvent event)
    {
        if (!event.getChannel().equals(SyncProxyConstants.SYNC_PROXY_CHANNEL_NAME)) return;

        switch (event.getMessage().toLowerCase())
        {
            case SyncProxyConstants.SYNC_PROXY_UPDATE_CONFIGURATION:
            {
                SyncProxyConfiguration syncProxyConfiguration = event.getData().get("syncProxyConfiguration", SyncProxyConfiguration.TYPE);

                if (syncProxyConfiguration != null)
                    SyncProxyConfigurationProvider.setLocal(syncProxyConfiguration);

                handlePlayerNotWhitelisted();
            }
            break;
        }
    }

    /*= ------------------------------------------------------------------------------------------------ =*/

    private void handlePlayerNotWhitelisted()
    {
        SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration = ProxProxCloudNetSyncProxyPlugin.getInstance().getProxyLoginConfiguration();

        if (syncProxyProxyLoginConfiguration != null)
            for (Player player : ProxProxCloudNetSyncProxyPlugin.getProxyServer().getPlayers())
                if (
                    syncProxyProxyLoginConfiguration.isMaintenance() &&
                        syncProxyProxyLoginConfiguration.getWhitelist() != null &&
                        !syncProxyProxyLoginConfiguration.getWhitelist().contains(player.getName()) &&
                        !syncProxyProxyLoginConfiguration.getWhitelist().contains(player.getUUID().toString()) &&
                        !player.hasPermission("cloudnet.syncproxy.maintenance"))
                {
                    player.kick(ChatColor.toANSI(SyncProxyConfigurationProvider.load().getMessages().get("player-login-not-whitelisted") + ""));
                }
    }
}