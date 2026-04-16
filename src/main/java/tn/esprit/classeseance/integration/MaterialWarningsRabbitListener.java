package tn.esprit.classeseance.integration;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tn.esprit.classeseance.service.SeanceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
public class MaterialWarningsRabbitListener {

    private final SeanceService seanceService;

    public MaterialWarningsRabbitListener(SeanceService seanceService) {
        this.seanceService = seanceService;
    }

    @RabbitListener(
            queues = IntegrationQueues.MATERIAL_WARNINGS,
            containerFactory = "rabbitListenerContainerFactory",
            errorHandler = "materialWarningsErrorHandler")
    public void onMaterialWarnings(@Payload Map<String, Object> body) {
        if (body == null) {
            return;
        }
        Object sourceObj = body.get("source");
        String source = sourceObj != null ? sourceObj.toString() : "MATERIAL";
        Object messagesObj = body.get("messages");
        if (!(messagesObj instanceof List<?> raw)) {
            return;
        }
        List<String> messages = new ArrayList<>();
        for (Object item : raw) {
            if (item != null) {
                messages.add(item.toString());
            }
        }
        seanceService.publishExternalWarnings(source, messages);
    }
}
