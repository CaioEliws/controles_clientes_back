package com.caio.controle_clientes.services.contrato;

import com.caio.controle_clientes.dto.contrato.*;
import com.caio.controle_clientes.exceptions.RegraNegocioException;
import com.caio.controle_clientes.models.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ContratoSnapshotService {
    public static final String VERSAO = "CONFISSAO_DIVIDA_V1";
    public static final ParteContratoDTO CREDOR = new ParteContratoDTO("Edson Antonio Vieira", "brasileiro",
            EstadoCivil.CASADO, "corretor de seguros", "17.888.629-4", "SSP/SP", "072.848.058-11",
            "Rua Humberto Batisti", 441, null, "Jardim Alice", "13346-253", "Indaiatuba", "SP");
    public static final TestemunhaFixaDTO TESTEMUNHA = new TestemunhaFixaDTO(
            "Ivani Elias Machado Vieira", "215.637.868-18", "30.623.886-X");

    public ContratoSnapshotDTO montar(DadosContratoDTO dados, LocalDate dataContrato) {
        validar(dados);
        EmprestimoContratoDTO e = dados.emprestimo();
        Map<String, String> campos = new LinkedHashMap<>();
        ContratoFormatador.pessoa(campos, "DEVEDOR", dados.devedor());
        ContratoFormatador.pessoa(campos, "FIADOR", dados.fiador());
        campos.put("VALOR_EMPRESTADO", ContratoFormatador.moeda(e.valorEmprestado()));
        campos.put("VALOR_EMPRESTADO_EXTENSO", NumeroPorExtenso.moeda(e.valorEmprestado()));
        campos.put("VALOR_TOTAL_DIVIDA", ContratoFormatador.moeda(e.valorTotalDivida()));
        campos.put("VALOR_TOTAL_DIVIDA_EXTENSO", NumeroPorExtenso.moeda(e.valorTotalDivida()));
        // Placeholders legados da cláusula 1: o Word oficial apresenta o principal nesse trecho.
        campos.put("VALOR_TOTAL", campos.get("VALOR_EMPRESTADO"));
        campos.put("VALOR_TOTAL_EXTENSO", campos.get("VALOR_EMPRESTADO_EXTENSO"));
        campos.put("VALOR_PROMISSORIA", campos.get("VALOR_EMPRESTADO"));
        campos.put("VALOR_PROMISSORIA_EXTENSO", campos.get("VALOR_EMPRESTADO_EXTENSO"));
        campos.put("QUANTIDADE_PARCELAS", e.quantidadeParcelas().toString());
        campos.put("QUANTIDADE_PARCELAS_EXTENSO", NumeroPorExtenso.quantidadeParcelasPorExtenso(e.quantidadeParcelas()));
        campos.put("PARCELA_SUBSTANTIVO", e.quantidadeParcelas() == 1 ? "parcela" : "parcelas");
        campos.put("VALOR_PARCELA", ContratoFormatador.moeda(e.valorParcela()));
        campos.put("VALOR_PARCELA_EXTENSO", NumeroPorExtenso.moeda(e.valorParcela()));
        var primeira = dados.parcelas().get(0).dataVencimento();
        var ultima = dados.parcelas().get(dados.parcelas().size()-1).dataVencimento();
        campos.put("DIA_VENCIMENTO", Integer.toString(primeira.getDayOfMonth()));
        campos.put("PRIMEIRA_PARCELA", ContratoFormatador.data(primeira));
        campos.put("ULTIMA_PARCELA", ContratoFormatador.data(ultima));
        if (e.quantidadeParcelas() == 1) {
            campos.put("VENCIMENTO_UNICO_EXTENSO", primeira.format(java.time.format.DateTimeFormatter
                    .ofPattern("dd 'de' MMMM 'de' yyyy", java.util.Locale.forLanguageTag("pt-BR"))));
        }
        campos.put("DATA_CONTRATO_EXTENSO", ContratoFormatador.dataExtenso(dataContrato));
        return new ContratoSnapshotDTO(dados.emprestimoId(), VERSAO, dataContrato, dados.devedor(),
                dados.fiador(), e, dados.parcelas(), CREDOR, TESTEMUNHA, campos);
    }

    private void validar(DadosContratoDTO d) {
        validarForma(d.devedor(),"devedor");
        validarForma(d.fiador(),"fiador");
        var e = d.emprestimo();
        if (e.frequenciaPagamento() != FrequenciaPagamento.MENSAL) {
            throw new RegraNegocioException("O modelo de contrato atual suporta apenas empréstimos com frequência MENSAL.");
        }
        if (e.dataDoEmprestimo() == null) erro("Data do empréstimo é obrigatória para o contrato.");
        if (d.parcelas().isEmpty()) erro("O empréstimo não possui parcelas para gerar contrato.");
        if (e.quantidadeParcelas() == null || e.quantidadeParcelas() <= 0
                || e.quantidadeParcelas() != d.parcelas().size()) erro("Quantidade de parcelas inconsistente para o contrato.");
        if (e.valorEmprestado() == null || e.valorEmprestado().signum() <= 0
                || e.valorTotalEmprestimo() == null || e.valorParcela() == null
                || e.valorTotalEmprestimo().signum() <= 0 || e.valorParcela().signum() <= 0) {
            erro("Valores contratuais obrigatórios ausentes ou inválidos.");
        }
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < d.parcelas().size(); i++) {
            var p = d.parcelas().get(i);
            if (p.numeroParcela() == null || p.numeroParcela() != i + 1 || p.dataVencimento() == null
                    || p.valorParcela() == null || p.valorParcela().signum() <= 0) {
                erro("Dados das parcelas ausentes ou inconsistentes para o contrato.");
            }
            if (p.valorParcela().compareTo(e.valorParcela()) != 0) {
                erro("O modelo atual exige parcelas com o mesmo valor contratual.");
            }
            if (!p.dataVencimento().equals(d.parcelas().get(0).dataVencimento().plusMonths(i))) {
                erro("As datas das parcelas são incompatíveis com o vencimento mensal fixo do modelo.");
            }
            soma = soma.add(p.valorParcela());
        }
        if (soma.compareTo(e.valorTotalEmprestimo()) != 0) {
            erro("Valor total e valores das parcelas inconsistentes para o contrato.");
        }
        // Somente valida precisão; não arredonda nem recalcula os valores financeiros.
        NumeroPorExtenso.moeda(e.valorTotalEmprestimo());
        NumeroPorExtenso.moeda(e.valorEmprestado());
        NumeroPorExtenso.moeda(e.valorParcela());
    }

    private void validarForma(ParteContratoDTO p, String papel) {
        if (p.sexoDocumento()==null) erro("Informe o sexo/forma gramatical do " + papel + ".");
        String orgao=ContratoFormatador.orgaoExpedidor(p.rgOrgaoExpedidor());
        if (!ContratoFormatador.orgaoExpedidorValido(orgao))
            erro("Informe a UF emissora do RG do " + papel + ".");
        if (!ContratoFormatador.ufValida(p.enderecoEstado()))
            erro("Informe uma UF do endereço válida para o " + papel + ".");
    }

    private void erro(String mensagem) { throw new RegraNegocioException(mensagem); }
}
