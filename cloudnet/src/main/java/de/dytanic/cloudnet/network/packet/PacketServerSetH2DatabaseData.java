package de.dytanic.cloudnet.network.packet;

import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.driver.network.def.PacketConstants;
import de.dytanic.cloudnet.driver.network.protocol.Packet;

import java.util.Map;

public final class PacketServerSetH2DatabaseData extends Packet {

    public PacketServerSetH2DatabaseData(Map<String, Map<String, JsonDocument>> documents)
    {
        super(PacketConstants.INTERNAL_H2_DATABASE_UPDATE_MODULE, new JsonDocument("set_h2db", true).append("documents", documents), new byte[0]);
    }
}