package com.example.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    public final static String URI_CREATE_DOCUMENT = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private int requestCounter;
    private final long[] timeStorage;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ReentrantLock lock = new ReentrantLock();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be greater than zero");
        }
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestCounter = 0;
        this.timeStorage = new long[requestLimit];
    }

    public CompletableFuture<HttpResponse<String>> createDocument(Document document, String token) {

        // Пояснение к блоку try:
        // В "закольцованный" массив размером N (requestLimit) записывается время начиная с которого
        // будет разрешено выполнение спустя N вызовов.
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            int index = requestCounter % requestLimit;
            long sleepTime = timeStorage[index] - currentTime;
            timeStorage[index] = currentTime + timeUnit.toMillis(1);
            if (sleepTime > 0) {
                try {
                    timeStorage[index] += sleepTime;
                    TimeUnit.MILLISECONDS.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        finally {
            requestCounter++;
            lock.unlock();
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(URI_CREATE_DOCUMENT))
                .POST(HttpRequest.BodyPublishers.ofString(objectToJson(document)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
    }


    public static String objectToJson(Object obj)  {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        objectMapper.setDateFormat(df);
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    @Getter
    @Setter
    @NoArgsConstructor
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private LocalDate production_date;
        private String production_type;
        private List<Product> products;
        private LocalDate reg_date;
        private String reg_number;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Product {
        String certificate_document;
        LocalDate certificate_document_date;
        String certificate_document_number;
        String owner_inn;
        String producer_inn;
        LocalDate production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;
    }


    @Getter
    @Setter
    @NoArgsConstructor
    public static class Description {
        private String participantInn;
    }


    public static void main(String[] args) throws InterruptedException, ExecutionException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 4);
        for (int i = 0; i < 15; i++){
            var document = new CrptApi.Document();
            document.setProduction_date(LocalDate.now());
            document.setDoc_id("Number " + i);
            System.out.println(crptApi.createDocument(document, "token").get().statusCode());
            Thread.sleep(100);
        }
    }

}
