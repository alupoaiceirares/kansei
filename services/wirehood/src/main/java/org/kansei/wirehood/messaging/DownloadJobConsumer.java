package org.kansei.wirehood.messaging;

import org.kansei.wirehood.download.DownloadWorkerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The listener container already runs on its own dedicated thread (not the WebFlux event loop), so blocking here to wait for the job to finish is fine - same as any synchronous @RabbitListener
 * The actual blocking I/O inside DownloadWorkerService still runs on Schedulers.boundedElastic(), this just waits for that chain to complete
 */
@Component
public class DownloadJobConsumer {

    private final DownloadWorkerService downloadWorkerService;

    public DownloadJobConsumer(DownloadWorkerService downloadWorkerService) {
        this.downloadWorkerService = downloadWorkerService;
    }

    @RabbitListener(queues = RabbitMQConfig.DOWNLOAD_JOBS_QUEUE)
    public void onMessage(DownloadJobMessage message) {
        downloadWorkerService.process(message).block();
    }
}
