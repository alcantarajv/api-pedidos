package com.joaoalcantara.pedidos.comum;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A documentacao OpenAPI.
 *
 * <p>Documentacao gerada quebra em silencio: um controller renomeado, uma
 * anotacao mal colocada, e a pagina continua abrindo — so que sem o endpoint. E
 * ninguem percebe, porque ninguem abre o Swagger todo dia.</p>
 *
 * <p>Estes testes verificam o que de fato importa: que o documento e gerado, que
 * os endpoints estao la, que o esquema de autenticacao existe (sem ele o botao
 * Authorize some e a pagina vira so uma lista) e que as rotas publicas nao
 * aparecem exigindo token.</p>
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class DocumentacaoIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("o documento OpenAPI e gerado e descreve a API")
    void documentoGerado() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("API de Pedidos e Pagamentos"));
    }

    @Test
    @DisplayName("todos os endpoints da API estao documentados")
    void endpointsDocumentados() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/registrar'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/produtos'].get").exists())
                .andExpect(jsonPath("$.paths['/api/produtos'].post").exists())
                .andExpect(jsonPath("$.paths['/api/produtos/{id}/estoque'].put").exists())
                .andExpect(jsonPath("$.paths['/api/produtos/{id}/situacao'].put").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos/{id}/cancelamento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos/{pedidoId}/pagamento'].post").exists())
                .andExpect(jsonPath("$.paths['/api/webhooks/gateway'].post").exists());
    }

    @Test
    @DisplayName("o cabecalho Idempotency-Key aparece como parametro obrigatorio")
    void idempotencyKeyDocumentada() throws Exception {
        // E a parte da API que um cliente NAO adivinha sozinho: sem ela na
        // documentacao, a primeira tentativa de criar pedido volta 422 e o
        // integrador fica sem saber por que.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.parameters[?(@.name == 'Idempotency-Key')]").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos'].post.responses['201']").exists());
    }

    @Test
    @DisplayName("o esquema de autenticacao esta declarado: e o que faz o botao Authorize existir")
    void esquemaDeSegurancaDeclarado() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists());
    }

    @Test
    @DisplayName("as rotas publicas nao aparecem exigindo token")
    void rotasPublicasSemExigencia() throws Exception {
        // A exigencia de token e global; as publicas precisam desmarca-la
        // individualmente. Esquecer isso faz o Swagger mandar Authorization no
        // login, e quem esta seguindo a documentacao fica preso num circulo:
        // precisa de token para obter o token.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/registrar'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/produtos'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/webhooks/gateway'].post.security").isEmpty());
    }

    @Test
    @DisplayName("a documentacao e alcancavel sem token, que e o proposito dela")
    void documentacaoPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
