package com.caio.controle_clientes.principal;

import org.springframework.stereotype.Component;

@Component
public class Principal {
    public void showMenu() {
        System.out.println("API RODANDO!");
    }

//    Scanner input = new Scanner(System.in);
//
//    private final ParcelaRepositorio parcelaRepositorio;
//
//    private final EmprestimoService emprestimoService;
//    private final ClienteService clienteService;
//    private final ParcelaService parcelaService;
//    private final RelatorioService relatorioService;
//
//    public Principal(EmprestimoService emprestimoService,
//                     ClienteService clienteService,
//                     ParcelaRepositorio parcelaRepositorio,
//                     ParcelaService parcelaService,
//                     RelatorioService relatorioService) {
//        this.emprestimoService = emprestimoService;
//        this.clienteService = clienteService;
//        this.parcelaRepositorio = parcelaRepositorio;
//        this.parcelaService = parcelaService;
//        this.relatorioService = relatorioService;
//    }
//
//    public void showMenu() {
//        int option = -1;
//
//        // Adicionar Crud (DELETE, EDIT)
//
//        while (option != 0) {
//            String menu = """
//                    \n1 - Cadastrar cliente
//                    2 - Criar empréstimo
//                    3 - Pagar parcela
//                    4 - Listar todas as parcelas pendentes
//                    5 - Listar todas parcelas atrasadas
//                    6 - Listar parcelas vencem hoje
//                    7 - Listar empréstimos e parcelas de um cliente
//                    8 - Filtrar parcelas deste mês
//                    9 - Ver histórico financeiro do cliente
//                    10 - Relatório de inadimplentes
//                    11 - Editar nome do cliente
//                    12 - Editar endereço do cliente
//                    13 - Deletar Cliente
//                    14 - Deletar emprestimo
//                    0 - Sair
//                    """;
//
//            System.out.println(menu);
//            option = input.nextInt();
//            input.nextLine();
//
//            switch (option) {
//                case 1 -> criarCliente();
//                case 2 -> criarEmprestimo();
//                case 3 -> pagarParcela();
//                case 4 -> listarParcelasPorStatus(ParcelaStatus.PENDENTE);
//                case 5 -> listarParcelasPorStatus(ParcelaStatus.ATRASADO);
//                case 6 -> listarParcelasVencendoHoje(LocalDate.now());
//                case 7 -> listarEmprestimos();
//                case 8 -> listarParcelasDoMesAtual();
//                case 9 -> mostrarHistoricoCliente();
//                case 10 -> mostrarRelatorioInadimplentes();
//                case 11 -> editNomeCliente();
//                case 12 -> editEnderecoCliente();
//                case 13 -> deleteCliente();
//                case 14 -> deleteEmprestimo();
//                case 0 -> System.out.println("Saindo...");
//                default -> System.out.println("Opção inválida");
//            }
//        }
//    }
//
//    private Cliente buscarClienteExistente() {
//        while (true) {
//            System.out.print("Digite o nome do cliente: ");
//            String nome = input.nextLine();
//
//            try {
//                return clienteService.buscarPorNome(nome);
//            } catch (RuntimeException e) {
//                System.out.println(e.getMessage());
//                System.out.println("Cliente não encontrado. Tente novamente.\n");
//            }
//        }
//    }
//
//    private void criarCliente() {
//        System.out.println("Digite o nome do cliente:");
//        String nomeCliente = input.nextLine();
//
//        System.out.println("Nome do indicador: ");
//        var nomeIndicador = input.nextLine();
//
//        System.out.println("Digite a rua do endereço: ");
//        var enderecoRua = input.nextLine();
//
//        System.out.println("Digite o bairro do endereço: ");
//        var enderecoBairro = input.nextLine();
//
//        System.out.println("Digite o numero do endereço: ");
//        Integer enderecoNumero = input.nextInt();
//        input.nextLine();
//
//        clienteService.criarCliente(nomeCliente,
//                nomeIndicador,
//                enderecoRua,
//                enderecoBairro,
//                enderecoNumero);
//
//        System.out.println("Cliente cadastrado com sucesso!");
//    }
//
//    private void deleteCliente() {
//        System.out.println("Digite o nome do cliente que deseja deletar:");
//        String nomeCliente = input.nextLine();
//
//        Cliente cliente;
//        try {
//            cliente = clienteService.buscarPorNome(nomeCliente);
//        } catch (RuntimeException e) {
//            System.out.println(e.getMessage());
//            return;
//        }
//
//        System.out.print(
//                "Tem certeza que deseja deletar o cliente " +
//                        cliente.getNome() + "? (s/n): "
//        );
//
//        String confirmacao = input.nextLine();
//
//        if (!confirmacao.equalsIgnoreCase("s")) {
//            System.out.println("Operação cancelada.");
//            return;
//        }
//
//        try {
//            clienteService.deletarCliente(cliente.getId());
//            System.out.println("Cliente deletado com sucesso!");
//        } catch (RuntimeException e) {
//            System.out.println("Erro: " + e.getMessage());
//        }
//    }
//
//    private void editNomeCliente() {
//        System.out.println("Digite o nome atual do cliente: ");
//        String nomeAtual = input.nextLine();
//
//        Cliente cliente;
//        try {
//            cliente = clienteService.buscarPorNome(nomeAtual);
//        } catch (RuntimeException e) {
//            System.out.println(e.getMessage());
//            return;
//        }
//
//        System.out.println("Digite o novo nome do cliente: ");
//        String novoNome = input.nextLine();
//
//        if (novoNome.isBlank()) {
//            System.out.println("Nome inválido!");
//            return;
//        }
//
//        clienteService.updateNameCliente(cliente.getId(), novoNome);
//        System.out.println("Nome do cliente atualizado com sucesso!");
//    }
//
//
//    private void editEnderecoCliente() {
//        System.out.println("Digite o nome do cliente: ");
//        String nomeCliente = input.nextLine();
//
//        Cliente cliente;
//        try {
//            cliente = clienteService.buscarPorNome(nomeCliente);
//        } catch (RuntimeException e) {
//            System.out.println(e.getMessage());
//            return;
//        }
//
//        System.out.println("Nova rua: ");
//        String rua = input.nextLine();
//
//        System.out.println("Novo bairro: ");
//        String bairro = input.nextLine();
//
//        System.out.println("Novo número: ");
//        Integer numero = input.nextInt();
//        input.nextLine();
//
//        if (rua.isBlank() || bairro.isBlank()) {
//            System.out.println("Endereço inválido!");
//            return;
//        }
//
//        clienteService.updateEnderecoCliente(
//                cliente.getId(),
//                rua,
//                bairro,
//                numero
//        );
//
//        System.out.println("Endereço atualizado com sucesso!");
//    }
//
//    private void criarEmprestimo() {
//        Cliente cliente = buscarClienteExistente();
//
//        FormaPagamento formaPagamento = PagamentoMenuService.escolherFormaPagamento();
//
//        System.out.println("Valor emprestado: ");
//        BigDecimal valorEmprestado = input.nextBigDecimal();
//
//        System.out.println("Quantidade de parcelas:");
//        int quantidadeParcelas = input.nextInt();
//
//        System.out.println("Juros cobrado (%): ");
//        BigDecimal jurosCobrado = input.nextBigDecimal();
//        input.nextLine();
//
//        emprestimoService.criarEmprestimo(
//                cliente,
//                valorEmprestado,
//                quantidadeParcelas,
//                jurosCobrado,
//                formaPagamento
//        );
//
//        System.out.println("Emprèstimo criado com sucesso!");
//    }
//
//    private void deleteEmprestimo() {
//        Cliente cliente = buscarClienteExistente();
//
//        List<Emprestimo> emprestimos =
//                emprestimoService.listarEmprestimosCliente(cliente);
//
//        if (emprestimos.isEmpty()) {
//            System.out.println("Esse cliente não possui empréstimos.");
//            return;
//        }
//
//        System.out.println("\nEmpréstimos do cliente:");
//
//        emprestimos.forEach(e -> {
//            System.out.println(
//                    "----------------------------" +
//                            "\nID: " + e.getId() +
//                            "\nTotal: " + e.getValorTotalEmprestimo() +
//                            "\nA Receber: " + e.getValorAReceber() +
//                            "\nStatus: " + e.getEmprestimoStatus() +
//                            "\n----------------------------"
//            );
//        });
//
//        System.out.print("\nDigite o ID do empréstimo que deseja deletar: ");
//        Long idEmprestimo = input.nextLong();
//        input.nextLine();
//
//        try {
//            emprestimoService.deletarEmprestimo(
//                    cliente.getId(),
//                    idEmprestimo
//            );
//
//            System.out.println("Empréstimo deletado com sucesso!");
//
//        } catch (RuntimeException e) {
//            System.out.println("Erro ao deletar empréstimo: " + e.getMessage());
//        }
//    }
//
//    private void pagarParcela() {
//        Cliente cliente = buscarClienteExistente();
//
//        List<Emprestimo> emprestimos =
//                emprestimoService.listarEmprestimosCliente(cliente);
//
//        if (emprestimos.isEmpty()) {
//            System.out.println("Esse cliente não possui empréstimos.");
//            return;
//        }
//
//        emprestimos.forEach(e -> {
//            System.out.println(
//                    "\n----------------------------" +
//                            "\nID: " + e.getId() +
//                            "\nTotal: " + e.getValorTotalEmprestimo() +
//                            "\nA Receber: " + e.getValorAReceber() +
//                            "\nStatus: " + e.getEmprestimoStatus() +
//                            "\n----------------------------"
//            );
//        });
//
//        System.out.print("\nDigite o ID do empréstimo: ");
//        Long idEmprestimo = input.nextLong();
//        input.nextLine();
//
//        List<Parcelas> parcelas =
//                emprestimoService.listarParcelas(idEmprestimo);
//
//        System.out.println("\nParcelas:");
//
//        parcelas.forEach(p -> {
//            BigDecimal saldo =
//                    p.getValorParcela().subtract(p.getValorPago());
//
//            System.out.println(
//                    "Parcela: " + p.getNumeroParcela() +
//                            " | Valor: " + p.getValorParcela() +
//                            " | Pago: " + p.getValorPago() +
//                            " | Saldo: " + saldo +
//                            " | Status: " + p.getStatus()
//            );
//        });
//
//        System.out.print("\nDigite o número da parcela: ");
//        Integer numeroParcela = input.nextInt();
//        input.nextLine();
//
//        System.out.print("Digite o valor a pagar: ");
//        String valorTexto = input.next().replace(",", ".");
//        BigDecimal valorPago = new BigDecimal(valorTexto);
//        input.nextLine();
//
//        try {
//            emprestimoService.pagarParcelaParcial(
//                    idEmprestimo,
//                    numeroParcela,
//                    valorPago
//            );
//
//            System.out.println("\nPagamento realizado com sucesso!");
//
//        } catch (EmprestimoQuitadoException | ParcelaPagaException e) {
//            System.out.println(e.getMessage());
//
//        } catch (RuntimeException e) {
//            System.out.println("Erro: " + e.getMessage());
//        }
//    }
//
//    private void listarParcelasPorStatus(ParcelaStatus status) {
//        parcelaService.atualizarParcelasAtrasadas();
//
//        List<Parcelas> parcelas = parcelaRepositorio.findParcelasComEmprestimoEClientePorStatus(status);
//
//        if (parcelas.isEmpty()) {
//            System.out.printf("Não há parcelas %s.%n", status.name().toLowerCase());
//            return;
//        }
//
//        parcelas.forEach(p -> {
//            BigDecimal saldo =
//                    p.getValorParcela().subtract(p.getValorPago());
//
//            System.out.printf(
//                    "Parcela %d | Valor: R$ %.2f | Pago: R$ %.2f | Saldo: R$ %.2f | Status: %s | Vencimento: %s%n",
//                    p.getNumeroParcela(),
//                    p.getValorParcela(),
//                    p.getValorPago(),
//                    saldo,
//                    p.getStatus(),
//                    p.getDataVencimento()
//            );
//        });
//    }
//
//    private void listarParcelasVencendoHoje(LocalDate dataVencimento) {
//        parcelaService.atualizarParcelasAtrasadas();
//
//
//        List<Parcelas> parcelas = parcelaRepositorio.findParcelasVencendoHoje(ParcelaStatus.PENDENTE, dataVencimento);
//
//        if (parcelas.isEmpty()) {
//            System.out.print("Não há parcelas vencendo hoje.");
//            return;
//        }
//
//        parcelas.forEach(p -> System.out.printf(
//                "Cliente: %s | Parcela %d | Valor emprestado: R$%.2f | Valor parcela: R$%.2f | Data de vencimento: %s%n",
//                p.getEmprestimo().getCliente().getNome(),
//                p.getNumeroParcela(),
//                p.getEmprestimo().getValorEmprestado(),
//                p.getValorParcela(),
//                p.getDataVencimento()
//        ));
//    }
//
//    private void listarEmprestimos() {
//
//        Cliente cliente = buscarClienteExistente();
//
//        List<Emprestimo> emprestimos =
//                emprestimoService.listarEmprestimosCliente(cliente);
//
//        if (emprestimos.isEmpty()) {
//            System.out.println("Esse cliente não possui empréstimos.");
//            return;
//        }
//
//        System.out.println("\nEmpréstimos:");
//
//        emprestimos.forEach(e -> {
//            System.out.printf(
//                    "ID: %d | Total: %s | A Receber: %s | Status: %s%n",
//                    e.getId(),
//                    e.getValorTotalEmprestimo(),
//                    e.getValorAReceber(),
//                    e.getEmprestimoStatus()
//            );
//        });
//
//        System.out.print("\nDigite o ID do empréstimo para ver parcelas: ");
//        Long idEmprestimo = input.nextLong();
//        input.nextLine();
//
//        Emprestimo emprestimoSelecionado = emprestimos.stream()
//                .filter(e -> e.getId().equals(idEmprestimo))
//                .findFirst()
//                .orElse(null);
//
//        if (emprestimoSelecionado == null) {
//            System.out.println("Empréstimo não encontrado.");
//            return;
//        }
//
//        List<Parcelas> parcelas =
//                emprestimoService.listarParcelas(idEmprestimo);
//
//        System.out.println("\nParcelas:");
//
//        parcelas.forEach(p -> {
//            System.out.printf(
//                    "Parcela %d | Valor: %s | Status: %s | Vencimento: %s%n",
//                    p.getNumeroParcela(),
//                    p.getValorParcela(),
//                    p.getStatus(),
//                    p.getDataVencimento()
//            );
//        });
//    }
//
//    private void listarParcelasDoMesAtual() {
//        parcelaService.atualizarParcelasAtrasadas();
//
//        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
//        LocalDate fimMes = LocalDate.now().withDayOfMonth(
//                LocalDate.now().lengthOfMonth()
//        );
//
//        List<Parcelas> parcelas = parcelaRepositorio.findParcelasDoMes(inicioMes, fimMes);
//
//        if (parcelas.isEmpty()) {
//            System.out.println("Não há parcelas para este mês.");
//            return;
//        }
//
//        System.out.println("\nParcelas deste mês:");
//        parcelas.forEach(p -> System.out.printf(
//                "Cliente: %s | Parcela %d | Valor: R$%.2f | Status: %s | Vencimento: %s%n",
//                p.getEmprestimo().getCliente().getNome(),
//                p.getNumeroParcela(),
//                p.getValorParcela(),
//                p.getStatus(),
//                p.getDataVencimento()
//        ));
//    }
//
//    private void mostrarHistoricoCliente() {
//        Cliente cliente = buscarClienteExistente();
//
//        var historico = relatorioService.gerarHistoricoCliente(cliente);
//
//        System.out.println("\n===== HISTÓRICO FINANCEIRO =====");
//
//        System.out.println("Cliente: " + cliente.getNome());
//
//        System.out.printf("Total emprestado: R$ %.2f%n",
//                historico.totalEmprestado());
//
//        System.out.printf("Total pago: R$ %.2f%n",
//                historico.totalPago());
//
//        System.out.printf("Total em aberto: R$ %.2f%n",
//                historico.totalAberto());
//
//        System.out.println("Parcelas atrasadas: " +
//                historico.parcelasAtrasadas());
//
//        System.out.println("Quantidade de empréstimos: " +
//                historico.totalEmprestimos());
//
//        System.out.println("===============================\n");
//    }
//
//    private void mostrarRelatorioInadimplentes() {
//
//        var inadimplentes = relatorioService.gerarRelatorioInadimplentes();
//
//        if (inadimplentes.isEmpty()) {
//            System.out.println("Não há clientes inadimplentes.");
//            return;
//        }
//
//        System.out.println("\n====== RELATÓRIO DE INADIMPLENTES ======\n");
//
//        inadimplentes.forEach(i -> {
//
//            System.out.println("Cliente: " + i.nomeCliente());
//            System.out.println("Parcelas em atraso: " + i.quantidadeParcelasAtrasadas());
//            System.out.printf("Valor em atraso: R$ %.2f%n", i.valorTotalEmAtraso());
//            System.out.println("Último vencimento: " + i.ultimoVencimento());
//
//            System.out.println("---------------------------------------");
//        });
//
//        BigDecimal totalGeral =
//                inadimplentes.stream()
//                        .map(InadimplenteDTO::valorTotalEmAtraso)
//                        .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        System.out.printf("\nTOTAL DE CLIENTES INADIMPLENTES: %d%n",
//                inadimplentes.size());
//
//        System.out.printf("VALOR TOTAL EM ATRASO: R$ %.2f%n",
//                totalGeral);
//
//        System.out.println("\n=======================================");
//    }
}
