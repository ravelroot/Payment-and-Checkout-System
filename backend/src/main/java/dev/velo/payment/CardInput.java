package dev.velo.payment;

public record CardInput(String holder, String number, String expiry, String cvv) {

    @Override
    public String toString() {
        return "CardInput[masked]";
    }
}
