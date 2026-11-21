package dev.plotnikov.fintrack.operation;

import dev.plotnikov.fintrack.account.AccountRepository;
import dev.plotnikov.fintrack.account.CategoryRepository;
import dev.plotnikov.fintrack.user.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final OperationService service;
    private final PhraseParser parser;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final CurrentUser currentUser;

    public OperationController(OperationService service, PhraseParser parser, AccountRepository accounts,
                               CategoryRepository categories, CurrentUser currentUser) {
        this.service = service;
        this.parser = parser;
        this.accounts = accounts;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Page<OperationDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long accountId,
                                   @RequestParam(required = false) Long officeId,
                                   @RequestParam(required = false) Long categoryId,
                                   @RequestParam(required = false) OperationKind kind,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return service.list(new OperationFilter(from, to, accountId, officeId, categoryId, kind, search, false), page, size);
    }

    @GetMapping("/{id}")
    public OperationDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public OperationDto create(@Valid @RequestBody OperationRequests.Create request) {
        return service.create(request, currentUser.get());
    }

    @PostMapping("/{id}/correct")
    public OperationDto correct(@PathVariable Long id, @Valid @RequestBody OperationRequests.Correct request) {
        return service.correct(id, request, currentUser.get());
    }

    @PostMapping("/parse")
    public PhraseParser.Draft parse(@Valid @RequestBody OperationRequests.Phrase request) {
        return parser.parse(request.text(), accounts.findAll(), categories.findAll());
    }
}
