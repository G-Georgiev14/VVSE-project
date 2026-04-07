package com.sanguine.gitbuild.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PingCommand {

    private static final String DEFAULT_URL = "http://127.0.0.1:8000/db-check";
    private static final int TIMEOUT_SECONDS = 3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ping")
                .executes(PingCommand::executePing)
        );
    }

    private static int executePing(CommandContext<CommandSourceStack> context) {
        long startTime = System.currentTimeMillis();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_URL))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long endTime = System.currentTimeMillis();
            long pingTime = endTime - startTime;

            if (response.statusCode() == 200) {
                context.getSource().sendSuccess(
                    () -> Component.literal("§aServer is online! Response: " + response.body() + " (" + pingTime + "ms)"),
                    false
                );
                return 1;
            } else {
                context.getSource().sendFailure(
                    Component.literal("§cServer returned status code: " + response.statusCode() + " (" + pingTime + "ms)")
                );
                return 0;
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long pingTime = endTime - startTime;
            context.getSource().sendFailure(
                Component.literal("§cCould not reach server (took " + pingTime + "ms): " + e.getMessage())
            );
            return 0;
        }
    }
}