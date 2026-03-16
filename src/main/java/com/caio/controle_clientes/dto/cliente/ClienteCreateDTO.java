package com.caio.controle_clientes.dto.cliente;

public record ClienteCreateDTO(String nome,
                               String nomeIndicador,
                               String enderecoRua,
                               String enderecoBairro,
                               Integer enderecoNumero
) {}
