package com.caio.controle_clientes;
import com.caio.controle_clientes.services.importClientes.IdentificadorClienteCsv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class IdentificadorClienteCsvTests {
    static Map<String,Object> campos(String nome,String cpf) { var m=new LinkedHashMap<String,Object>();m.put("nome",nome);m.put("cpf",cpf);return m; }
    static IdentificadorClienteCsv.Registro registro(long id,String nome,String cpf) { return new IdentificadorClienteCsv.Registro(id,campos(nome,cpf)); }
    @ParameterizedTest @CsvSource(value={"João da Silva|  joão da silva  ","Maria  Aparecida da Silva| Maria Aparecida da Silva ","João da Silva|JOAO DA SILVA","Adriele Aparecida Bento|adriele aparecida bento"},delimiter='|')
    void nomeNormalizadoPreservaRegistroSemCpf(String banco,String csv) {
        var indice=new IdentificadorClienteCsv.Indice(List.of(registro(1,banco,null)));
        var m=indice.encontrar(campos(csv,"00000000002"));
        assertThat(m.tipo()).isEqualTo("NOME");assertThat(m.registro().campos().get("nome")).isEqualTo(banco);assertThat(m.registro().id()).isEqualTo(1);
    }
    @Test void cpfFallbackNaoRenomeiaOuDuplica() {
        var i=new IdentificadorClienteCsv.Indice(List.of(registro(1,"João C. Silva","000.000.000-02")));
        assertThat(i.encontrar(campos("João Carlos Silva","00000000002")).tipo()).isEqualTo("CPF");
    }
    @Test void nomeECpfDeClientesDiferentesBloqueiam() {
        var i=new IdentificadorClienteCsv.Indice(List.of(registro(1,"João Silva","00000000002"),registro(2,"José Silva","00000000003")));
        assertThat(i.encontrar(campos("João Silva","00000000003")).bloqueio()).isEqualTo("CONFLITO_IDENTIDADE");
    }
    @Test void homonimosSaoResolvidosSomentePorCpfInequivoco() {
        var i=new IdentificadorClienteCsv.Indice(List.of(registro(1,"José da Silva",null),registro(2,"JOSE DA SILVA","00000000003")));
        assertThat(i.encontrar(campos("José da Silva",null)).bloqueio()).isEqualTo("AMBIGUO_NOME");
        var m=i.encontrar(campos("José da Silva","00000000003"));assertThat(m.tipo()).isEqualTo("CPF");assertThat(m.registro().id()).isEqualTo(2);
    }
    @Test void novoEAtualizacaoDoIndiceNaoGuardamCpfAntigo() {
        var i=new IdentificadorClienteCsv.Indice(List.of());assertThat(i.encontrar(campos("Cliente","00000000002")).tipo()).isEqualTo("NOVO");
        i.atualizar(registro(1,"Cliente","00000000002"));i.atualizar(registro(1,"Outro","00000000003"));
        assertThat(i.encontrar(campos("Cliente","00000000002")).tipo()).isEqualTo("NOVO");
    }
}
