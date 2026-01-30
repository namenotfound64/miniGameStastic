package net.minegate.plugin.miniGameStatistic.api;

import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * CloudNet v4 API Wrapper using Driver API
 * Based on implementation from TalexCK/GameVoting
 */
public class CloudNetAPI {
    private static CloudNetAPI instance;
    
    private final CloudServiceProvider serviceProvider;

    private CloudNetAPI() {
        // Get providers from CloudNet's dependency injection layer
        this.serviceProvider = InjectionLayer.ext().instance(CloudServiceProvider.class);
    }

    public static void initialize() {
        if (instance == null) {
            instance = new CloudNetAPI();
        }
    }

    public static CloudNetAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CloudNetAPI not initialized!");
        }
        return instance;
    }

    /**
     * Get a specific service by name
     */
    public Optional<ServiceInfoSnapshot> getServiceByName(String name) {
        return serviceProvider.services().stream()
            .filter(service -> service.name().equalsIgnoreCase(name))
            .findFirst();
    }

    /**
     * Execute a command on a specific service.
     * Used for sending players to other servers via proxy command.
     * 
     * @param serviceName The name of the service
     * @param command The command to execute
     */
    public void executeServiceCommand(String serviceName, String command) {
        Optional<ServiceInfoSnapshot> serviceOpt = getServiceByName(serviceName);
        if (serviceOpt.isEmpty()) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }
        
        UUID serviceId = serviceOpt.get().serviceId().uniqueId();
        serviceProvider.serviceProvider(serviceId).runCommand(command);
    }
}
