package com.gigpay.earnings.worker;

import java.time.LocalDate;
import java.util.List;

public record Worker(String id, String name, String phone, String city, String vehicle, String upiId,
                     List<String> platforms, String language, LocalDate joinedOn) {
}
