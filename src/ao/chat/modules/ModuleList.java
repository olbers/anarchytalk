/*
 * ModuleList.java
 *
 * Created on Sep 16, 2010, 4:00 PM
 *************************************************************************
 * Copyright 2010 Kevin Kendall
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
import ao.protocol.BotStateException;
import ao.misc.ByteConvert;
import java.util.Vector;

public class ModuleList {

    public enum ModuleChannel {
        TELL, PRIVATE, GROUP, CON;
    }
    private Vector<Module> modules = new Vector();
    private ChatBot chatbot;
    private ModuleUser user;
    private String prefix;
    private boolean cononly;

    public ModuleList(ChatBot bot, ModuleUser u, String prefix) {
        chatbot = bot;
        user = u;
        this.prefix = prefix;
        cononly = false;
    }

    public ModuleList(ChatBot bot, ModuleUser u, String prefix, boolean con) {
        chatbot = bot;
        user = u;
        this.prefix = prefix;
        cononly = con;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String p) {
        prefix = p;
    }

    public String helpMenu() {
        if (chatbot == null) {
            throw new NullPointerException("A reference to the bot was not supplied.");
        }

        String botName = null;
        try{
            botName = chatbot.getCharacter().getName();
        } catch (Exception e){
            botName = "Unknown";
        }
        String help = "<a href=\"text://";
        for (Module module : modules) {
            help += "<font color='#00ff00'>" + module.getName() + "</font><br>";

            String[] commands = module.getCommands();
            for (String command : commands) {
                help += " * <a href='chatcmd:///tell " + botName
                        + " " + prefix + "help " + command + "'>" + command + "</a><br>";
            }   // end for
            help += "<br>";
        }   // end for
        help += "\">Help Menu</a>";
        return help;
    }

    public int getModules() {
        return modules.size();
    }

    public void add(Module module) {
        modules.add(module);
    }

    public Module getModule(int i) {
        return modules.elementAt(i);
    }

    public void remove(Module module) {
        modules.remove(module);
    }

    public void remove(int i) {
        modules.remove(i);
    }

    public void execute(ModuleChannel channel, byte[] id, String command, String[] args) {
        if (!cononly || channel == ModuleChannel.CON) {
            command = command.toLowerCase();
            if (chatbot == null) {
                throw new NullPointerException("A reference to the bot was not supplied.");
            }

            if (command.compareTo("prefix") == 0 && args.length <= 0) {
                reply(channel, id, "This bots prefix is currently set to \"" + prefix + "\"");
            } else if (command.compareTo("help") == 0 && args.length <= 0) {
                reply(channel, id, helpMenu());
            } else if (command.compareTo("help") == 0 && args.length > 0 && args[0] != null) {
                for (Module module : modules) {
                    String[] commands = module.getCommands();
                    for (String cmd : commands) {
                        if (cmd.compareTo(args[0]) == 0) {
                            reply(channel, id, module.help(chatbot, prefix, args[0]));
                        }
                    }
                }
            } else {
                for (Module module : modules) {
                    String[] commands = module.getCommands();
                    for (String cmd : commands) {
                        if (cmd.compareTo(command) == 0) {
                            module.execute(chatbot, user, channel, id, prefix, command, args);
                        }
                    }
                }
            }
        }
    }

    private void reply(ModuleChannel channel, byte[] id, String msg) {
        if (chatbot == null) {
            throw new NullPointerException("A reference to the bot was not supplied.");
        }

        try {
            switch (channel) {
                case TELL:
                    checkLoggedIn();
                    chatbot.sendTell(ByteConvert.byteToInt(id), msg);
                    break;
                case PRIVATE:
                    checkLoggedIn();
                    chatbot.sendPrivateChannelMessage(ByteConvert.byteToInt(id), msg);
                    break;
                case GROUP:
                    checkLoggedIn();
                    chatbot.sendChannelMessage(id, msg);
                    break;
                case CON:
                    user.println(chatbot, msg);
                    break;
                default:
                    System.out.println("Channel reply error");
            }
        } catch (Exception e) {
            user.exception(chatbot, e);
        }
    }

    private void checkLoggedIn(){
        // Make sure the bot is logged in
        ChatBot.State botState = chatbot.getState();
        if (botState != ChatBot.State.LOGGED_IN) {
            throw new BotStateException("The bot must be logged in to perform this action.", botState, ChatBot.State.LOGGED_IN);
        }   // end if
    }
}
