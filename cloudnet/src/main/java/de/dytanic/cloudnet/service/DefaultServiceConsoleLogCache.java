package de.dytanic.cloudnet.service;

import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.common.collection.Iterables;
import de.dytanic.cloudnet.common.logging.LogLevel;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.service.ServiceLifeCycle;
import de.dytanic.cloudnet.event.service.CloudServiceConsoleLogReceiveEntryEvent;
import de.dytanic.cloudnet.network.packet.PacketServerConsoleLogEntryReceive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Queue;

@Getter
@RequiredArgsConstructor
public final class DefaultServiceConsoleLogCache implements IServiceConsoleLogCache {

    private final Queue<String> cachedLogMessages = Iterables.newConcurrentLinkedQueue();

    private final byte[] buffer = new byte[1024];

    private final StringBuffer stringBuffer = new StringBuffer();

    //*=====================================================================================

    private final ICloudService cloudService;

    //*=====================================================================================

    @Getter
    @Setter
    private boolean autoPrintReceivedInput;

    //*=====================================================================================

    private int len;

    @Override
    public synchronized IServiceConsoleLogCache update()
    {
        if (cloudService.getLifeCycle() == ServiceLifeCycle.RUNNING && cloudService.isAlive() && cloudService.getProcess() != null)
        {
            readStream(cloudService.getProcess().getInputStream(), false);
            readStream(cloudService.getProcess().getErrorStream(), CloudNet.getInstance().getConfig().isPrintErrorStreamLinesFromServices());
        }
        return this;
    }

    private synchronized void readStream(InputStream inputStream, boolean printErrorIntoConsole)
    {
        try
        {
            while (inputStream.available() > 0 && (len = inputStream.read(buffer, 0, buffer.length)) != -1)
                stringBuffer.append(new String(buffer, 0, len, StandardCharsets.UTF_8));

            String stringText = stringBuffer.toString();
            if (!stringText.contains("\n") && !stringText.contains("\r")) return;

            for (String input : stringText.split("\r"))
                for (String text : input.split("\n"))
                    if (!text.trim().isEmpty())
                        this.addCachedItem(text, printErrorIntoConsole);

            stringBuffer.setLength(0);

        } catch (Exception ignored)
        {
            stringBuffer.setLength(0);
        }
    }

    private void addCachedItem(String text, boolean printErrorIntoConsole)
    {
        if (text == null) return;

        while (cachedLogMessages.size() >= CloudNet.getInstance().getConfig().getMaxServiceConsoleLogCacheSize())
            cachedLogMessages.poll();

        cachedLogMessages.offer(text);

        CloudNetDriver.getInstance().getEventManager().callEvent(new CloudServiceConsoleLogReceiveEntryEvent(cloudService.getServiceInfoSnapshot(), text, printErrorIntoConsole));
        CloudNet.getInstance().getClusterNodeServerProvider().sendPacket(new PacketServerConsoleLogEntryReceive(cloudService.getServiceInfoSnapshot(), text, printErrorIntoConsole));

        if (this.autoPrintReceivedInput || printErrorIntoConsole)
            CloudNetDriver.getInstance().getLogger().log((printErrorIntoConsole ? LogLevel.WARNING : LogLevel.INFO), "[" + cloudService.getServiceId().getName() + "] " + text);
    }
}