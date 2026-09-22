package com.joaoalcantara.pedidos.comum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentacao OpenAPI da API.
 *
 * <p>O esquema de seguranca e declarado aqui, e nao endpoint a endpoint: assim o
 * botao "Authorize" do Swagger UI passa a existir, o token e colado uma vez e
 * vale para todas as chamadas seguintes. Sem isso, cada requisicao protegida
 * voltaria 401 e a pagina viraria so uma lista bonita.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI documentacaoDaApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Pedidos e Pagamentos")
                        .version("v1")
                        .description("""
                                API de pedidos com pagamento por gateway externo.

                                O ponto tecnico central nao e o CRUD: e o que acontece quando a \
                                aplicacao depende de outro sistema.

                                **Idempotencia de entrada.** POST /api/pedidos exige o cabecalho \
                                `Idempotency-Key`. Reenviar a mesma chave devolve o pedido ja criado \
                                com 200, em vez de criar um segundo com 201. Isso torna seguro repetir \
                                a requisicao depois de um timeout — o caso em que o cliente nao sabe \
                                se o pedido entrou.

                                **Idempotencia de saida.** O webhook do gateway chega mais de uma vez, \
                                por definicao. O identificador do evento e gravado numa tabela com \
                                restricao de unicidade, na mesma transacao que aplica o efeito: a \
                                segunda entrega colide e nao produz efeito novo.

                                **Entrega garantida.** Confirmado o pagamento, o evento e gravado numa \
                                tabela de outbox na mesma transacao do pedido, e publicado depois por \
                                um processo separado. Nao ha janela em que o pedido esteja pago e o \
                                evento nao exista.

                                ### Como usar esta pagina

                                1. Registre-se em `POST /api/auth/registrar` (cria sempre um CLIENTE).
                                2. Obtenha o token em `POST /api/auth/login`.
                                3. Clique em **Authorize** e cole o token.

                                Erros seguem o formato Problem Details (RFC 9457), com um campo `type` \
                                estavel que pode ser tratado programaticamente.

                                Valores monetarios em BRL com duas casas; datas em ISO-8601 UTC.
                                """)
                        .contact(new Contact()
                                .name("Joao Vitor Alcantara Correa")
                                .url("https://github.com/alcantarajv"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token devolvido por /api/auth/login. Cole apenas o token, sem o prefixo Bearer.")))
                // Exigencia global: a maioria das rotas precisa de token. As
                // publicas sao marcadas individualmente com @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
