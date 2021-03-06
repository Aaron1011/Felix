/*
 * This file is part of felix, licensed under the MIT License.
 *
 * Copyright (c) Korobi <https://korobi.io>
 * Copyright (c) SpongePowered <https://spongepowered.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.spongepowered.felix.platform;

import com.sk89q.intake.context.CommandContext;
import org.spongepowered.felix.command.CommandConfiguration;
import org.spongepowered.felix.command.CommandUtil;
import org.spongepowered.felix.command.PhysicalCommand;
import org.spongepowered.felix.command.Target;
import ninja.leaping.configurate.ConfigurationNode;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.RequestBuffer;

import java.util.Iterator;

import javax.annotation.Nullable;

public final class DiscordPlatform {
  private final IDiscordClient client;
  private final CommandConfiguration cc;

  public DiscordPlatform(final ConfigurationNode config, final CommandConfiguration cc) {
    this.cc = cc;
    this.client = new ClientBuilder()
      .withToken(config.getNode("token").getString())
      .build();
    this.client.getDispatcher().registerListener(this);
    this.client.login();
  }

  @EventSubscriber
  public void messageReceiver(final MessageReceivedEvent event) {
    if(this.cc.ignored(event.getAuthor().getStringID())) {
      return;
    }

    final String message = event.getMessage().getContent();
    if(message.length() < 2) {
      return;
    }

    if(message.charAt(0) != this.cc.prefix) {
      return;
    }

    final String arguments = message.substring(1);
    final String[] split = CommandContext.split(arguments);
    final String tempName = split[0].toLowerCase();
    final Target targetType = Target.of(tempName);
    final String name = tempName.substring(targetType.substring);

    // Let's get physical.
    @Nullable final PhysicalCommand command = this.cc.commands.get(name);
    if(command == null) {
      return;
    }

    final StringBuilder sb = new StringBuilder();
    for(final Iterator<String> iterator = command.responses.iterator(); iterator.hasNext(); ) {
      final String value = iterator.next();
      sb.append(CommandUtil.wrapPrefix(this.cc.prefix, name, value));
      if(iterator.hasNext()) {
        sb.append('\n');
      }
    }
    RequestBuffer.request(() -> event.getChannel().sendMessage(sb.toString()));
  }
}
