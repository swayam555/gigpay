package com.gigpay.ledger.api;

import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Ledger;
import com.gigpay.ledger.payout.Limits;
import com.gigpay.ledger.payout.Payout;
import com.gigpay.ledger.payout.PayoutRepository;
import com.gigpay.ledger.payout.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/** The worker's wallet. The worker id comes from the gateway-verified token, never from the request body. */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService wallets;
    private final PayoutRepository payouts;
    private final Ledger ledger;

    public WalletController(WalletService wallets, PayoutRepository payouts, Ledger ledger) {
        this.wallets = wallets;
        this.payouts = payouts;
        this.ledger = ledger;
    }

    @GetMapping
    public WalletService.Wallet wallet(@RequestHeader("X-User-Id") String workerId) {
        return wallets.wallet(workerId);
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<Payout> withdraw(@RequestHeader("X-User-Id") String workerId,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @RequestBody WithdrawRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wallets.withdraw(workerId, req.amountPaise(), req.upiId(), key));
    }

    @GetMapping("/payouts")
    public List<Payout> payouts(@RequestHeader("X-User-Id") String workerId) {
        return payouts.forWorker(workerId, 30);
    }

    @GetMapping("/payouts/{id}")
    public Payout payout(@RequestHeader("X-User-Id") String workerId, @PathVariable String id) {
        return payouts.find(id).filter(p -> p.workerId().equals(workerId))
                .orElseThrow(() -> new NoSuchElementException("Payout not found"));
    }

    /** Every rupee in and out, straight from the ledger postings on the worker's account. */
    @GetMapping("/statement")
    public List<Ledger.Line> statement(@RequestHeader("X-User-Id") String workerId,
                                       @RequestParam(defaultValue = "50") int limit) {
        return ledger.statement(Accounts.workerPayable(workerId), Math.min(limit, 200));
    }

    @ExceptionHandler(Limits.LimitExceededException.class)
    ProblemDetail limit(Limits.LimitExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail bad(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    public record WithdrawRequest(long amountPaise, String upiId) {
    }
}
