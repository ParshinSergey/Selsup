package com.example.selsup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

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

    public String createDocument(Document document, String token) {

        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            timeStorage[requestCounter % (requestLimit)] = currentTime;
            long limitTime = timeStorage[(requestCounter + 1) % (requestLimit)] + timeUnit.toMillis(1);
            if (currentTime < limitTime) {
                long sleepTime = limitTime - currentTime;
                try {
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

        String bodyRequest;
        try {
            bodyRequest = objectToJson(document);
        } catch (JsonProcessingException e) {
            return "Error processing (parsing, generating) JSON content " + e.getMessage();
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .POST(HttpRequest.BodyPublishers.ofString(bodyRequest))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return "Error connection to URL " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error connection to URL " + e.getMessage();
        }

        return httpResponse.body();
    }

    public static String objectToJson(Object obj) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        objectMapper.setDateFormat(df);
        return objectMapper.writeValueAsString(obj);
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

    public static void main(String[] args) throws InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 4);
        for (int i = 0; i < 12; i++){
            var document = new CrptApi.Document();
            document.setProduction_date(LocalDate.now());
            document.setDoc_id("12345");
            System.out.println(crptApi.createDocument(document, "token"));
            Thread.sleep(600);
        }
    }

}
