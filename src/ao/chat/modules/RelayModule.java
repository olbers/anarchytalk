/*
 * RelayModule.java
 *
 * Created on February 5, 2011, 3:46 AM
 *************************************************************************
 * Copyright 2011 Kevin Kendall
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ao.chat.modules;

import ao.chat.ChatBot;
import ao.chat.modules.ModuleList.ModuleChannel;
import ao.protocol.Bot;
import ao.protocol.PrivateChannelListener;
import ao.protocol.ChannelListener;
import ao.protocol.packets.bi.ChannelMessagePacket;
import ao.protocol.packets.bi.PrivateChannelMessagePacket;

public class RelayModule implements Module, PrivateChannelListener, ChannelListener {

    private String name = "Relay Module";
    private String[] commands = {
        "init"
    };

    public RelayModule() {
    }

    public String getName() {
        return name;
    }

    public String[] getCommands() {
        return commands;
    }

    public String help(ChatBot chatbot, String prefix, String command) {
        String help = "<a href=\"text://";
        help += "<font color='#00ff00'>" + command + "</font><br><br>";
        if (command.compareTo(commands[0]) == 0) {//init
            help += "used to initialize this module";
        }
        help += "<br>";
        help += "\">" + command + " Help</a>";
        return help;
    }

    public void execute(ChatBot chatbot, ModuleUser user, ModuleChannel channel, byte[] id, String prefix, String command, String[] args) {
        try {
            if (command.compareTo(commands[0]) == 0) {//init
                chatbot.addPrivateChannelListener(this);
                chatbot.addChannelListener(this);
            }
        } catch (Exception e) {
            user.exception(chatbot, e);
        }
    }

    public void channelMessagePacket(Bot bot, ChannelMessagePacket packet) {
        if (bot instanceof ChatBot) {
            ChatBot chatbot = (ChatBot) bot;
            byte[] relay = chatbot.getOrgID();
            byte[] groupID = packet.getGroupID();
            int botID = chatbot.getCharacter().getID();
            boolean isRelay = false;
            if (relay != null && relay.length == groupID.length) {
                isRelay = true;
                for (int i = 0; i < relay.length; i++) {
                    if (relay[i] != groupID[i]) {
                        isRelay = false;
                    }
                }
            }
            if (relay != null && isRelay && packet.getCharID() != botID) {
                String orgName = chatbot.getOrdName();
                String charName = chatbot.getCharTable().getName(packet.getCharID());
                try {
                    chatbot.sendPrivateChannelMessage(botID, "[" + orgName + "] " + charName + ": " + packet.getMessage());
                } catch (Exception e) {
                }
            }
        }
    }

    public void privateChannelPacket(Bot bot, PrivateChannelMessagePacket packet) {
        if (bot instanceof ChatBot) {
            ChatBot chatbot = (ChatBot) bot;
            byte[] relay = chatbot.getOrgID();
            int botID = chatbot.getCharacter().getID();
            if (packet.getGroupID() == botID && packet.getCharID() != botID && relay != null) {
                String botName = chatbot.getCharacter().getName();
                String charName = chatbot.getCharTable().getName(packet.getCharID());
                try {
                    chatbot.sendChannelMessage(relay, "[" + botName + "'s Guest] " + charName + ": " + packet.getMessage());
                } catch (Exception e) {
                }
            }
        }
    }
}
