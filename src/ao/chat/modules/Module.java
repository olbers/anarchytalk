/*
 * Module.java
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
import ao.chat.modules.ModuleList.ModuleChannel;

public interface Module {
    String getName();
    String[] getCommands();
    String help(ChatBot chatbot, String prefix, String command);
    void execute(ChatBot chatbot, ModuleUser user, ModuleChannel channel, byte[] id, String prefix, String command, String[] args);
}