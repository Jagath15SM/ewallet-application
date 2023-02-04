package com.example.majorproject;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.HttpRetryException;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

@Service
public class TransactionService {

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    @Autowired
    RestTemplate restTemplate;

    public void createTransaction(TransactionRequestDto transactionRequestDto) throws JsonProcessingException {

        //First of all we will create a transaction Entity and put its status to pending

        Transaction transaction = Transaction.builder().fromUser(transactionRequestDto.getFromUser())
                        .toUser(transactionRequestDto.getToUser()).transactionId(UUID.randomUUID().toString())
                        .transactionDate(new Date()).transactionStatus(TransactionStatus.PENDING)
                .amount(transactionRequestDto.getAmount()).purpose(transactionRequestDto.getPurpose()).build();
        transactionRepository.save(transaction);

        //Create that JsonObject
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fromUser", transactionRequestDto.getFromUser());
        jsonObject.put("toUser", transactionRequestDto.getToUser());
        jsonObject.put("amount", transactionRequestDto.getAmount());
        jsonObject.put("transactionId",transaction.getTransactionId());

        //Converted to JSON object to string and send it via kafka to the wallet microservice
        String kafkaMessage = objectMapper.writeValueAsString(jsonObject);
        kafkaTemplate.send("update_wallet",kafkaMessage);
        System.out.println(kafkaMessage);
    }

    @KafkaListener(topics = "update_transaction", groupId = "friends_group")
    public void updateTransaction(String message) throws JsonProcessingException {

        JSONObject jsonObject = objectMapper.readValue(message,JSONObject.class);

        String transactionStatus = (String) jsonObject.get("status");
        String transactionId = (String) jsonObject.get("transactionId");

        System.out.println("Reading the transactionTable Entries"+transactionStatus+"---"+transactionId);

        Transaction transaction = transactionRepository.findByTransactionId(transactionId);
        transaction.setTransactionStatus(TransactionStatus.valueOf(transactionStatus));
        transactionRepository.save(transaction);

        // CALL NOTIFICATION SERVICE AND SEND EMAILS
        callNotificationService(transaction);
    }

    private void callNotificationService(Transaction transaction) {
        String fromUserName = transaction.getFromUser();
        String toUserName = transaction.getToUser();

        // We need to create REST API, call user service
        URI url = URI.create("http://localhost:9999/user/find-EmailDto/"+fromUserName);
        HttpEntity httpEntity = new HttpEntity(new HttpHeaders());

        JSONObject fromUserJsonObject = restTemplate.
                exchange(url, HttpMethod.GET, httpEntity, JSONObject.class).getBody();
        String senderName = (String) fromUserJsonObject.get("name");
        String senderEmail = (String) fromUserJsonObject.get("email");


        url = URI.create("http://localhost:9999/user/find-EmailDto/"+toUserName);
        httpEntity = new HttpEntity(new HttpHeaders());

        JSONObject toUserJsonObject = restTemplate.
                exchange(url, HttpMethod.GET, httpEntity, JSONObject.class).getBody();
        String receiverName = (String) toUserJsonObject.get("name");
        String receiverEmail = (String) toUserJsonObject.get("email");

        //SEND THE EMAIL AND MESSAGE TO NOTIFICATIONS-SERVICE VIA KAFKA

        // Sender should always receive the Email
        JSONObject emailRequest = new JSONObject();
        System.out.println("We are in transaction Service Layer"+senderName+" "+senderEmail+" "+receiverName+" "+receiverEmail);
        emailRequest.put("email", senderEmail);
        String senderMessageBody = String.format("Hi %s \n" +
                "The transaction with transactionId %s has been %s of Rs %d .",
                senderName, transaction.getTransactionId(), transaction.getTransactionStatus(), transaction.getAmount());
        emailRequest.put("message", senderMessageBody);

        String message = emailRequest.toString();
        kafkaTemplate.send("send_email",message); // sending via kafka

        if(transaction.getTransactionStatus().equals("FAILED")) return;

        // Receiver should receive an email on Success transaction only
        emailRequest = new JSONObject();
        emailRequest.put("email",receiverEmail);
        String receiverMessageBody = String.format(
                "Hi %s \n" + "You have received an amount of %d Rs from %s",
                receiverName, transaction.getAmount(),senderName);
        emailRequest.put("message", receiverMessageBody);
        message = emailRequest.toString();
        kafkaTemplate.send("send_email", message);
    }

}
