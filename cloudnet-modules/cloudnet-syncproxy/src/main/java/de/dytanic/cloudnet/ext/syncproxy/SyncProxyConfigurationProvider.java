package de.dytanic.cloudnet.ext.syncproxy;

import de.dytanic.cloudnet.common.Validate;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.driver.CloudNetDriver;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class SyncProxyConfigurationProvider {

    private static SyncProxyConfiguration loadedConfiguration;

    private SyncProxyConfigurationProvider()
    {
        throw new UnsupportedOperationException();
    }

    public static void setLocal(SyncProxyConfiguration syncProxyConfiguration)
    {
        Validate.checkNotNull(syncProxyConfiguration);

        loadedConfiguration = syncProxyConfiguration;
    }

    public static SyncProxyConfiguration load()
    {
        if (loadedConfiguration == null)
        {
            loadedConfiguration = load0();
        }

        return loadedConfiguration;
    }

    private static SyncProxyConfiguration load0()
    {
        ITask<SyncProxyConfiguration> task = CloudNetDriver.getInstance().sendCallablePacket(CloudNetDriver.getInstance().getNetworkClient().getChannels().iterator().next(),
            SyncProxyConstants.SYNC_PROXY_SYNC_CHANNEL_PROPERTY,
            SyncProxyConstants.SIGN_CHANNEL_SYNC_ID_GET_SYNC_PROXY_CONFIGURATION_PROPERTY,
            new JsonDocument(),
            new Function<JsonDocument, SyncProxyConfiguration>() {
                @Override
                public SyncProxyConfiguration apply(JsonDocument documentPair)
                {
                    return documentPair.get("syncProxyConfiguration", SyncProxyConfiguration.TYPE);
                }
            });

        try
        {
            return task.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            e.printStackTrace();
        }

        return null;
    }
}