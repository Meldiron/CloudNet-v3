package de.dytanic.cloudnet.ext.bridge.gomint;

import de.dytanic.cloudnet.common.collection.Iterables;
import de.dytanic.cloudnet.common.collection.Maps;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.common.concurrent.ITaskListener;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.network.HostAndPort;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.driver.service.ServiceTask;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.WorldInfo;
import de.dytanic.cloudnet.ext.bridge.WorldPosition;
import de.dytanic.cloudnet.ext.bridge.player.NetworkConnectionInfo;
import de.dytanic.cloudnet.ext.bridge.player.NetworkPlayerServerInfo;
import de.dytanic.cloudnet.ext.bridge.player.NetworkServiceInfo;
import de.dytanic.cloudnet.wrapper.Wrapper;
import io.gomint.GoMint;
import io.gomint.entity.EntityPlayer;
import io.gomint.math.Location;
import io.gomint.server.GoMintServer;
import io.gomint.server.network.Protocol;
import io.gomint.world.Gamerule;
import io.gomint.world.World;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class GoMintCloudNetHelper {

    @Getter
    @Setter
    private static volatile String
        apiMotd,
        extra = "",
        state = "LOBBY";

    @Getter
    @Setter
    private static volatile int maxPlayers = GoMint.instance().getMaxPlayers();

    @Getter
    @Setter
    private static volatile GoMintCloudNetBridgePlugin plugin;

    static
    {
        try
        {
            apiMotd = GoMint.instance().getMotd();
        } catch (Exception ignored)
        {
            apiMotd = "";
        }
    }

    //*= ----------------------------------------------------------------

    private GoMintCloudNetHelper()
    {
        throw new UnsupportedOperationException();
    }

    public static GoMintServer getGoMintServer()
    {
        return (GoMintServer) GoMint.instance();
    }

    public static void changeToIngame()
    {
        state = "INGAME";
        BridgeHelper.updateServiceInfo();

        String task = Wrapper.getInstance().getServiceId().getTaskName();

        if (!CloudNetDriver.getInstance().isServiceTaskPresent(task))
        {
            CloudNetDriver.getInstance().getServiceTaskAsync(task).addListener(new ITaskListener<ServiceTask>() {

                @Override
                public void onComplete(ITask<ServiceTask> task, ServiceTask serviceTask)
                {
                    if (serviceTask != null)
                    {
                        CloudNetDriver.getInstance().createCloudServiceAsync(serviceTask).addListener(new ITaskListener<ServiceInfoSnapshot>() {

                            @Override
                            public void onComplete(ITask<ServiceInfoSnapshot> task, ServiceInfoSnapshot serviceInfoSnapshot)
                            {
                                if (serviceInfoSnapshot != null)
                                    CloudNetDriver.getInstance().startCloudService(serviceInfoSnapshot);
                            }
                        });
                    }
                }
            });
        }
    }

    public static void initProperties(ServiceInfoSnapshot serviceInfoSnapshot)
    {
        serviceInfoSnapshot.getProperties()
            .append("Online", true)
            .append("Version", Protocol.MINECRAFT_PE_NETWORK_VERSION)
            .append("GoMint-Version", GoMint.instance().getVersion())
            .append("Max-Players", maxPlayers)
            .append("Motd", apiMotd)
            .append("Extra", extra)
            .append("State", state)
            .append("TPS", GoMint.instance().getTPS());

        if (GoMint.instance().isMainThread())
            serviceInfoSnapshot.getProperties()
                .append("Online-Count", GoMint.instance().getPlayers().size())
                .append("Players", Iterables.map(GoMint.instance().getPlayers(), new Function<EntityPlayer, GoMintCloudNetPlayerInfo>() {

                    @Override
                    public GoMintCloudNetPlayerInfo apply(EntityPlayer entityPlayer)
                    {
                        Location location = entityPlayer.getLocation();

                        io.gomint.server.entity.EntityPlayer player = (io.gomint.server.entity.EntityPlayer) entityPlayer;

                        return new GoMintCloudNetPlayerInfo(
                            entityPlayer.getUUID(),
                            entityPlayer.isOnline(),
                            entityPlayer.getName(),
                            entityPlayer.getDeviceInfo().getDeviceName(),
                            entityPlayer.getXboxID(),
                            entityPlayer.getGamemode().name(),
                            entityPlayer.getHealth(),
                            entityPlayer.getMaxHealth(),
                            entityPlayer.getSaturation(),
                            entityPlayer.getLevel(),
                            entityPlayer.getPing(),
                            entityPlayer.getLocale(),
                            new WorldPosition(
                                location.getX(),
                                location.getY(),
                                location.getZ(),
                                location.getYaw(),
                                location.getPitch(),
                                location.getWorld().getWorldName()
                            ),
                            new HostAndPort(player.getConnection().getConnection().getAddress())
                        );
                    }
                }))
                .append("Worlds", Iterables.map(GoMint.instance().getWorlds(), new Function<World, WorldInfo>() {
                    @Override
                    public WorldInfo apply(World world)
                    {
                        Map<String, String> gameRules = Maps.newHashMap();

                        for (Field field : Gamerule.class.getFields())
                            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) &&
                                Modifier.isPublic(field.getModifiers()) && Gamerule.class.isAssignableFrom(field.getType()))
                            {
                                try
                                {
                                    field.setAccessible(true);
                                    Gamerule<?> gameRule = (Gamerule<?>) field.get(null);
                                    gameRules.put(gameRule.getNbtName(), world.getGamerule(gameRule) + "");

                                } catch (IllegalAccessException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                        return new WorldInfo(
                            UUID.randomUUID(),
                            world.getLevelName(),
                            world.getDifficulty().name(),
                            gameRules
                        );
                    }
                }))
                ;
    }

    public static NetworkConnectionInfo createNetworkConnectionInfo(EntityPlayer entityPlayer)
    {
        io.gomint.server.entity.EntityPlayer player = (io.gomint.server.entity.EntityPlayer) entityPlayer;

        return BridgeHelper.createNetworkConnectionInfo(
            player.getUUID(),
            player.getName(),
            Protocol.MINECRAFT_PE_PROTOCOL_VERSION,
            new HostAndPort(player.getConnection().getConnection().getAddress()),
            new HostAndPort(getGoMintServer().getServerConfig().getListener().getIp(), GoMint.instance().getPort()),
            true,
            false,
            new NetworkServiceInfo(
                ServiceEnvironmentType.GO_MINT,
                Wrapper.getInstance().getServiceId().getUniqueId(),
                Wrapper.getInstance().getServiceId().getName()
            )
        );
    }

    public static NetworkPlayerServerInfo createNetworkPlayerServerInfo(EntityPlayer entityPlayer, boolean login)
    {
        WorldPosition worldPosition;

        if (login)
            worldPosition = new WorldPosition(-1, -1, -1, -1, -1, "world");
        else
        {
            Location location = entityPlayer.getLocation();

            worldPosition = new WorldPosition(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                location.getWorld().getWorldName()
            );
        }

        io.gomint.server.entity.EntityPlayer player = (io.gomint.server.entity.EntityPlayer) entityPlayer;

        return new NetworkPlayerServerInfo(
            entityPlayer.getUUID(),
            entityPlayer.getName(),
            entityPlayer.getXboxID(),
            entityPlayer.getHealth(),
            entityPlayer.getMaxHealth(),
            entityPlayer.getSaturation(),
            entityPlayer.getLevel(),
            worldPosition,
            new HostAndPort(player.getConnection().getConnection().getAddress()),
            new NetworkServiceInfo(
                ServiceEnvironmentType.GO_MINT,
                Wrapper.getInstance().getServiceId().getUniqueId(),
                Wrapper.getInstance().getServiceId().getName()
            )
        );
    }
}