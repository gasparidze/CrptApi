package org.example;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpResponse.BodyHandlers.*;

/**
 * Класс для работы с API Честного знака
 */
public class CrptApi {
    /**
     * URL, по которому будем осуществлять запрос
     */
    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    /**
     * количество запросов на создание документа
     */
    private static final int REQUEST_QUANTITY = 10;

    /**
     * объект ObjectMapper для преобразования java объекта в JSON формат
     */
    private ObjectMapper objectMapper;

    /**
     * объект HttpClient для осуществления rest запросов
     */
    private HttpClient httpClient;

    /**
     * промежуток времени
     */
    private TimeUnit timeUnit;

    /**
     * максимальное количество запросов в указанном промежутке времени
     */
    private int requestLimit;

    /**
     * очередь запросов на создание документа
     */
    private Queue<Document> requestQueue;

    /**
     * планировщик потоков
     */
    private ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit){
        objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        requestQueue = new LinkedList<>();
        scheduler = Executors.newScheduledThreadPool(1);
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
        Document document = getDocument(crptApi);
        String signature = "test_signature";

        for (int i = 1; i <= REQUEST_QUANTITY; i++) {
            document.doc_id = String.valueOf(i);
            crptApi.createDocument(document, signature);
        }
    }

    /**
     * метод, осуществляющий инициализацию объекта Document
     * @param crptApi - объект CrptApi
     * @return Document - документ
     */
    private static Document getDocument(CrptApi crptApi){
        Document.Product product = new Document.Product("testCert", "2020-01-23",
                "123", "123456789", "123456789",
                "2020-01-23", "testTnvedCode", "testUnitCode", "testUituCode");

        return crptApi.new Document(
                new Document.Description("123456789"),
                "test", "testStatus", "testDocType", true, "123456789",
                "123456789", "123456789", "2020-01-23",
                "testProductionType", new ArrayList<>(Arrays.asList(product)),
                "2020-01-23", "123456789"
        );
    }

    /**
     * метод, осуществляющий создание документа
     * @param document - документ
     * @param signature - подпись
     */
    public synchronized void createDocument(Document document, String signature){
        requestQueue.offer(document);
        scheduler.scheduleAtFixedRate(() -> processRequestQueue(signature), 0, 1, timeUnit);
    }

    /**
     * метод, обрабатывающий очередь запросов на создание документа
     * @param signature - подпись
     */
    private void processRequestQueue(String signature) {
        int processedRequests = 0;
        while (!requestQueue.isEmpty() && processedRequests < requestLimit) {
            makeRequest(requestQueue.poll(), signature);
            processedRequests++;
        }
        if (requestQueue.isEmpty()) {
            scheduler.shutdown();
        }
    }

    /**
     * метод, осуществляющий rest запрос на создание документа
     * @param document - документ
     * @param signature - подпись
     */
    public void makeRequest(Document document, String signature){
        try {
            String requestBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, ofString());

            if (response.statusCode() == 200) {
                System.out.println("Документ успешно создан!");
            } else {
                System.out.println("Cтатус код: " + response.statusCode());
                System.out.println("Ошибка при создании документа: " + response.body());
            }
        } catch (Exception e){
            System.out.println("Ошибка при отправке запроса: " + e.getMessage());
        }
    }

    /**
     * Класс документа
     */
    class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        public Document(){}
        public Document(Description description, String doc_id, String doc_status, String doc_type,
                         boolean importRequest, String owner_inn, String participant_inn, String producer_inn,
                         String production_date, String production_type, List<Product> products, String reg_date,
                         String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        /**
         * Класс описания документа
         */
        public static class Description {
            private String participantInn;

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }

        /**
         * Класс продукта документа
         */
        public static class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;

            public Product(String certificate_document, String certificate_document_date,
                           String certificate_document_number, String owner_inn, String producer_inn,
                           String production_date, String tnved_code, String uit_code, String uitu_code) {
                this.certificate_document = certificate_document;
                this.certificate_document_date = certificate_document_date;
                this.certificate_document_number = certificate_document_number;
                this.owner_inn = owner_inn;
                this.producer_inn = producer_inn;
                this.production_date = production_date;
                this.tnved_code = tnved_code;
                this.uit_code = uit_code;
                this.uitu_code = uitu_code;
            }
        }
    }
}
