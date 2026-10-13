package com.caio.controle_clientes.services.contrato;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.caio.controle_clientes.exceptions.RegraNegocioException;

public final class NumeroPorExtenso {
    private NumeroPorExtenso() {}
    private static final String[] UNIDADES = "zero,um,dois,três,quatro,cinco,seis,sete,oito,nove,dez,onze,doze,treze,catorze,quinze,dezesseis,dezessete,dezoito,dezenove".split(",");
    private static final String[] DEZENAS = "zero,dez,vinte,trinta,quarenta,cinquenta,sessenta,setenta,oitenta,noventa".split(",");
    private static final String[] CENTENAS = "zero,cento,duzentos,trezentos,quatrocentos,quinhentos,seiscentos,setecentos,oitocentos,novecentos".split(",");
    private static final String[] SINGULAR = {"", "mil", "milhão", "bilhão", "trilhão"};
    private static final String[] PLURAL = {"", "mil", "milhões", "bilhões", "trilhões"};
    public static String inteiro(long numero) {
        return inteiro(numero, false);
    }

    public static String quantidadeParcelasPorExtenso(long numero) {
        return inteiro(numero, true);
    }

    private static String inteiro(long numero, boolean feminino) {
        if (numero < 0 || numero > 999_999_999_999_999L) {
            throw new RegraNegocioException("Número fora do limite suportado pelo modelo contratual.");
        }
        if (numero == 0) return UNIDADES[0];
        List<String> grupos = new ArrayList<>();
        List<Integer> valores = new ArrayList<>();
        for (int nivel = 0; numero > 0; nivel++, numero /= 1000) {
            int grupo = (int) (numero % 1000);
            if (grupo == 0) continue;
            // Mil agrees with the counted noun; milhão/bilhão/trilhão remain masculine.
            String texto = nivel == 1 && grupo == 1 ? "mil" : centenas(grupo, feminino && nivel <= 1)
                    + (nivel == 0 ? "" : " " + (grupo == 1 ? SINGULAR[nivel] : PLURAL[nivel]));
            grupos.add(0, texto);
            valores.add(0, grupo);
        }
        StringBuilder resultado = new StringBuilder(grupos.get(0));
        for (int i = 1; i < grupos.size(); i++) {
            int grupo = valores.get(i);
            resultado.append(i == grupos.size() - 1 && (grupo < 100 || grupo % 100 == 0) ? " e " : " ");
            resultado.append(grupos.get(i));
        }
        return resultado.toString();
    }

    private static String centenas(int numero, boolean feminino) {
        if (numero < 20) return feminino && numero == 1 ? "uma" : feminino && numero == 2 ? "duas" : UNIDADES[numero];
        if (numero == 100) return "cem";
        if (numero >= 100) {
            String centena = CENTENAS[numero / 100];
            if (feminino && numero >= 200) centena = centena.substring(0, centena.length() - 2) + "as";
            return centena + (numero % 100 == 0 ? "" : " e " + centenas(numero % 100, feminino));
        }
        return DEZENAS[numero / 10] + (numero % 10 == 0 ? "" : " e " + centenas(numero % 10, feminino));
    }

    public static String moeda(BigDecimal valor) {
        try {
            BigDecimal exato = valor.setScale(2, RoundingMode.UNNECESSARY);
            if (exato.signum() < 0) throw new ArithmeticException();
            long reais = exato.setScale(0, RoundingMode.DOWN).longValueExact();
            int centavos = exato.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
            String texto = reais == 0 && centavos > 0 ? "" : inteiro(reais)
                    + (reais == 1 ? " real" : reais > 0 && reais % 1_000_000 == 0 ? " de reais" : " reais");
            if (centavos > 0) texto += (texto.isEmpty() ? "" : " e ") + inteiro(centavos)
                    + (centavos == 1 ? " centavo" : " centavos");
            return texto;
        } catch (ArithmeticException | NullPointerException e) {
            throw new RegraNegocioException("Valor monetário inválido para o modelo contratual.");
        }
    }
}
