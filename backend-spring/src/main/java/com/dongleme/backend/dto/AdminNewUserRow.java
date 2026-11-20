package com.dongleme.backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminNewUserRow {

    private LocalDate date;

    private Long newUsers;
}
