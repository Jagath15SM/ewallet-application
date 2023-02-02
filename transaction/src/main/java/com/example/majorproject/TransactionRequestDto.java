package com.example.majorproject;


import lombok.Data;

@Data
public class TransactionRequestDto {

    private String fromUser;
    private String toUser;
    private int amount;
    private String purpose;

}
