package br.com.felipeblaft.relatorios.configuracao;

import br.com.felipeblaft.relatorios.casodeuso.acesso.AtualizarConversaTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.ConsultarVinculoTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EncerrarSessaoWebCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EncerrarVinculoTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EntrarPortalCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.IdentificarProfessorTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.ValidarSessaoWebCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.VincularTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.AprovarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.CancelarRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ObterRelatorioEmAbertoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.RegistrarAulaPorAudioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.ResponderPerguntasCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.SolicitarAlteracaoRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.aula.TentarNovamenteRelatorioAulaCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.CarregarCadastroInicialCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.EmitirCodigoAcessoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.documento.ObterDocumentoRelatorioCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterInicioProfessorCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterResumoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.AprovarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.CancelarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.GerarRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ListarSemestresDisponiveisCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterPreviaSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.ObterRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.semestral.SolicitarAlteracaoRelatorioSemestralCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.ConsultarTrabalhoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.trabalho.DescartarRegistroEmProcessamentoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.ArmazenamentoDocumentoPorta;
import br.com.felipeblaft.relatorios.porta.ArquivoCanalPorta;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.DocumentoPdfRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta;
import br.com.felipeblaft.relatorios.porta.GeradorCodigoAcessoPorta;
import br.com.felipeblaft.relatorios.porta.InteracaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.LlmPorta;
import br.com.felipeblaft.relatorios.porta.NormalizacaoAudioPorta;
import br.com.felipeblaft.relatorios.porta.NotificacaoProfessorPorta;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RegistroCustoPorta;
import br.com.felipeblaft.relatorios.porta.RelatorioAulaRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatorioSemestralRepositorio;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import br.com.felipeblaft.relatorios.porta.TentativaAcessoRepositorio;
import br.com.felipeblaft.relatorios.porta.TransacaoPorta;
import br.com.felipeblaft.relatorios.porta.TranscricaoPorta;
import br.com.felipeblaft.relatorios.porta.VersaoRelatorioRepositorio;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import br.com.felipeblaft.relatorios.servico.AcessoRelatorioServico;
import br.com.felipeblaft.relatorios.servico.AcessoSemestralServico;
import br.com.felipeblaft.relatorios.servico.AudioTemporarioServico;
import br.com.felipeblaft.relatorios.servico.AutenticacaoCodigoServico;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import br.com.felipeblaft.relatorios.servico.ControleTentativasAcessoServico;
import br.com.felipeblaft.relatorios.servico.EmissaoCodigoAcessoServico;
import br.com.felipeblaft.relatorios.servico.EnvioAudioServico;
import br.com.felipeblaft.relatorios.servico.GeracaoDocumentoServico;
import br.com.felipeblaft.relatorios.servico.ProcessadorSemestralServico;
import br.com.felipeblaft.relatorios.servico.ProcessadorTrabalhoServico;
import br.com.felipeblaft.relatorios.servico.RenovacaoAcessoServico;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Instancia os casos de uso e serviços do núcleo como beans. O núcleo não tem anotações de
 * framework; toda a montagem fica aqui.
 */
@Configuration(proxyBeanMethods = false)
public class CasosDeUsoConfiguracao {

    @Bean
    ParametrosNegocio parametrosNegocio(PropriedadesAplicacao propriedades) {
        return new ParametrosNegocio(
                Duration.ofDays(propriedades.codigo().validadeDias()),
                propriedades.acesso().maxTentativas(),
                Duration.ofMinutes(propriedades.acesso().bloqueioMinutos()),
                Duration.ofHours(propriedades.sessao().horas()),
                Duration.ofMinutes(propriedades.telegram().audioPendenteMinutos()),
                propriedades.audio().limiteBytes());
    }

    // ---- Serviços

    @Bean
    AutorizacaoServico autorizacaoServico(AssociacaoRepositorio associacoes) {
        return new AutorizacaoServico(associacoes);
    }

    @Bean
    EmissaoCodigoAcessoServico emissaoCodigoAcessoServico(
            GeradorCodigoAcessoPorta gerador,
            ProtecaoDadosPorta protecao,
            ProfessorRepositorio professores,
            ParametrosNegocio parametros) {
        return new EmissaoCodigoAcessoServico(gerador, protecao, professores, parametros);
    }

    @Bean
    ControleTentativasAcessoServico controleTentativasAcessoServico(
            TentativaAcessoRepositorio tentativas,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            ParametrosNegocio parametros) {
        return new ControleTentativasAcessoServico(tentativas, protecao, relogio, parametros);
    }

    @Bean
    RenovacaoAcessoServico renovacaoAcessoServico(
            ProfessorRepositorio professores, RelogioPorta relogio, ParametrosNegocio parametros) {
        return new RenovacaoAcessoServico(professores, relogio, parametros);
    }

    @Bean
    AutenticacaoCodigoServico autenticacaoCodigoServico(
            ControleTentativasAcessoServico tentativas,
            ProfessorRepositorio professores,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio) {
        return new AutenticacaoCodigoServico(tentativas, professores, protecao, relogio);
    }

    // ---- Carga inicial

    @Bean
    CarregarCadastroInicialCasoDeUso carregarCadastroInicialCasoDeUso(
            ProfessorRepositorio professores,
            AlunoRepositorio alunos,
            AssociacaoRepositorio associacoes,
            ProtecaoDadosPorta protecao,
            EmissaoCodigoAcessoServico emissao,
            RelogioPorta relogio) {
        return new CarregarCadastroInicialCasoDeUso(
                professores, alunos, associacoes, protecao, emissao, relogio);
    }

    @Bean
    EmitirCodigoAcessoCasoDeUso emitirCodigoAcessoCasoDeUso(
            ProfessorRepositorio professores,
            EmissaoCodigoAcessoServico emissao,
            RelogioPorta relogio) {
        return new EmitirCodigoAcessoCasoDeUso(professores, emissao, relogio);
    }

    // ---- Acesso

    @Bean
    EntrarPortalCasoDeUso entrarPortalCasoDeUso(
            AutenticacaoCodigoServico autenticacao,
            RenovacaoAcessoServico renovacao,
            SessaoWebRepositorio sessoes,
            VinculoTelegramRepositorio vinculos,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            ParametrosNegocio parametros) {
        return new EntrarPortalCasoDeUso(
                autenticacao, renovacao, sessoes, vinculos, protecao, relogio, parametros);
    }

    @Bean
    ValidarSessaoWebCasoDeUso validarSessaoWebCasoDeUso(
            SessaoWebRepositorio sessoes,
            ProfessorRepositorio professores,
            RenovacaoAcessoServico renovacao,
            ProtecaoDadosPorta protecao,
            RelogioPorta relogio,
            ParametrosNegocio parametros) {
        return new ValidarSessaoWebCasoDeUso(
                sessoes, professores, renovacao, protecao, relogio, parametros);
    }

    @Bean
    EncerrarSessaoWebCasoDeUso encerrarSessaoWebCasoDeUso(
            SessaoWebRepositorio sessoes, ProtecaoDadosPorta protecao) {
        return new EncerrarSessaoWebCasoDeUso(sessoes, protecao);
    }

    @Bean
    VincularTelegramCasoDeUso vincularTelegramCasoDeUso(
            AutenticacaoCodigoServico autenticacao,
            RenovacaoAcessoServico renovacao,
            VinculoTelegramRepositorio vinculos,
            RelogioPorta relogio) {
        return new VincularTelegramCasoDeUso(autenticacao, renovacao, vinculos, relogio);
    }

    @Bean
    IdentificarProfessorTelegramCasoDeUso identificarProfessorTelegramCasoDeUso(
            VinculoTelegramRepositorio vinculos,
            ProfessorRepositorio professores,
            RenovacaoAcessoServico renovacao) {
        return new IdentificarProfessorTelegramCasoDeUso(vinculos, professores, renovacao);
    }

    @Bean
    EncerrarVinculoTelegramCasoDeUso encerrarVinculoTelegramCasoDeUso(
            VinculoTelegramRepositorio vinculos) {
        return new EncerrarVinculoTelegramCasoDeUso(vinculos);
    }

    @Bean
    AtualizarConversaTelegramCasoDeUso atualizarConversaTelegramCasoDeUso(
            VinculoTelegramRepositorio vinculos, RelogioPorta relogio) {
        return new AtualizarConversaTelegramCasoDeUso(vinculos, relogio);
    }

    @Bean
    ConsultarVinculoTelegramCasoDeUso consultarVinculoTelegramCasoDeUso(
            VinculoTelegramRepositorio vinculos) {
        return new ConsultarVinculoTelegramCasoDeUso(vinculos);
    }

    // ---- Início e alunos

    @Bean
    ObterInicioProfessorCasoDeUso obterInicioProfessorCasoDeUso(
            AlunoRepositorio alunos, RelatoriosEmAbertoConsultaPorta consulta) {
        return new ObterInicioProfessorCasoDeUso(alunos, consulta);
    }

    @Bean
    BuscarAlunosCasoDeUso buscarAlunosCasoDeUso(
            AlunoRepositorio alunos, RelatoriosEmAbertoConsultaPorta consulta) {
        return new BuscarAlunosCasoDeUso(alunos, consulta);
    }

    @Bean
    ObterResumoAlunoCasoDeUso obterResumoAlunoCasoDeUso(
            AlunoRepositorio alunos,
            RelatoriosEmAbertoConsultaPorta consulta,
            AutorizacaoServico autorizacao) {
        return new ObterResumoAlunoCasoDeUso(alunos, consulta, autorizacao);
    }

    // ---- Registro de aula (US1): serviços

    @Bean
    AcessoRelatorioServico acessoRelatorioServico(
            RelatorioAulaRepositorio relatorios, AssociacaoRepositorio associacoes) {
        return new AcessoRelatorioServico(relatorios, associacoes);
    }

    @Bean
    AudioTemporarioServico audioTemporarioServico(
            ArquivoCanalPorta arquivoCanal,
            NormalizacaoAudioPorta normalizacao,
            TranscricaoPorta transcricao) {
        return new AudioTemporarioServico(arquivoCanal, normalizacao, transcricao);
    }

    @Bean
    EnvioAudioServico envioAudioServico(
            ParametrosNegocio parametros, AudioTemporarioServico audio) {
        return new EnvioAudioServico(parametros, audio);
    }

    @Bean
    GeracaoDocumentoServico geracaoDocumentoServico(
            GeracaoPdfPorta geracaoPdf,
            ArmazenamentoDocumentoPorta armazenamento,
            DocumentoPdfRepositorio documentos,
            RelatorioAulaRepositorio relatorios,
            AlunoRepositorio alunos,
            ProfessorRepositorio professores,
            RelogioPorta relogio) {
        return new GeracaoDocumentoServico(
                geracaoPdf, armazenamento, documentos, relatorios, alunos, professores, relogio);
    }

    @Bean
    @SuppressWarnings("java:S107")
    ProcessadorTrabalhoServico processadorTrabalhoServico(
            FilaTrabalhoPorta fila,
            RelatorioAulaRepositorio relatorios,
            VersaoRelatorioRepositorio versoes,
            InteracaoRelatorioRepositorio interacoes,
            AudioTemporarioServico audio,
            LlmPorta llm,
            GeracaoDocumentoServico documentos,
            NotificacaoProfessorPorta notificacao,
            RegistroCustoPorta custos,
            TransacaoPorta transacao,
            RelogioPorta relogio) {
        return new ProcessadorTrabalhoServico(
                fila,
                relatorios,
                versoes,
                interacoes,
                audio,
                llm,
                documentos,
                notificacao,
                custos,
                transacao,
                relogio);
    }

    // ---- Registro de aula (US1): casos de uso

    @Bean
    RegistrarAulaPorAudioCasoDeUso registrarAulaPorAudioCasoDeUso(
            AutorizacaoServico autorizacao,
            RelatoriosEmAbertoConsultaPorta emAberto,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new RegistrarAulaPorAudioCasoDeUso(autorizacao, emAberto, envioAudio, fila, relogio);
    }

    @Bean
    ResponderPerguntasCasoDeUso responderPerguntasCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new ResponderPerguntasCasoDeUso(acesso, relatorios, envioAudio, fila, relogio);
    }

    @Bean
    SolicitarAlteracaoRelatorioAulaCasoDeUso solicitarAlteracaoRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new SolicitarAlteracaoRelatorioAulaCasoDeUso(
                acesso, relatorios, envioAudio, fila, relogio);
    }

    @Bean
    TentarNovamenteRelatorioAulaCasoDeUso tentarNovamenteRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new TentarNovamenteRelatorioAulaCasoDeUso(acesso, relatorios, fila, relogio);
    }

    @Bean
    AprovarRelatorioAulaCasoDeUso aprovarRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            VersaoRelatorioRepositorio versoes,
            GeracaoDocumentoServico documentos,
            RelogioPorta relogio) {
        return new AprovarRelatorioAulaCasoDeUso(acesso, relatorios, versoes, documentos, relogio);
    }

    @Bean
    CancelarRelatorioAulaCasoDeUso cancelarRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new CancelarRelatorioAulaCasoDeUso(acesso, relatorios, fila, relogio);
    }

    @Bean
    @SuppressWarnings("java:S107")
    ObterRelatorioAulaCasoDeUso obterRelatorioAulaCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            AlunoRepositorio alunos,
            ProfessorRepositorio professores,
            InteracaoRelatorioRepositorio interacoes,
            VersaoRelatorioRepositorio versoes,
            DocumentoPdfRepositorio documentos,
            FilaTrabalhoPorta fila) {
        return new ObterRelatorioAulaCasoDeUso(
                acesso, relatorios, alunos, professores, interacoes, versoes, documentos, fila);
    }

    @Bean
    ConsultarTrabalhoCasoDeUso consultarTrabalhoCasoDeUso(
            FilaTrabalhoPorta fila, RelogioPorta relogio) {
        return new ConsultarTrabalhoCasoDeUso(fila, relogio);
    }

    @Bean
    ObterDocumentoRelatorioCasoDeUso obterDocumentoRelatorioCasoDeUso(
            AcessoRelatorioServico acesso,
            RelatorioAulaRepositorio relatorios,
            VersaoRelatorioRepositorio versoes,
            DocumentoPdfRepositorio documentos,
            GeracaoDocumentoServico geracao) {
        return new ObterDocumentoRelatorioCasoDeUso(
                acesso, relatorios, versoes, documentos, geracao);
    }

    // ---- Retomada entre canais (US3)

    @Bean
    ObterRelatorioEmAbertoCasoDeUso obterRelatorioEmAbertoCasoDeUso(
            AutorizacaoServico autorizacao, RelatoriosEmAbertoConsultaPorta consulta) {
        return new ObterRelatorioEmAbertoCasoDeUso(autorizacao, consulta);
    }

    @Bean
    DescartarRegistroEmProcessamentoCasoDeUso descartarRegistroEmProcessamentoCasoDeUso(
            FilaTrabalhoPorta fila, AudioTemporarioServico audio, RelogioPorta relogio) {
        return new DescartarRegistroEmProcessamentoCasoDeUso(fila, audio, relogio);
    }

    // ---- Relatório semestral (US4)

    @Bean
    AcessoSemestralServico acessoSemestralServico(
            RelatorioSemestralRepositorio semestrais, AssociacaoRepositorio associacoes) {
        return new AcessoSemestralServico(semestrais, associacoes);
    }

    @Bean
    @SuppressWarnings("java:S107")
    ProcessadorSemestralServico processadorSemestralServico(
            RelatorioSemestralRepositorio semestrais,
            RelatorioAulaRepositorio aulas,
            AlunoRepositorio alunos,
            ProfessorRepositorio professores,
            VersaoRelatorioRepositorio versoes,
            InteracaoRelatorioRepositorio interacoes,
            GeracaoDocumentoServico documentos,
            FilaTrabalhoPorta fila,
            TransacaoPorta transacao,
            RelogioPorta relogio,
            ProcessadorTrabalhoServico processador) {
        ProcessadorSemestralServico semestral =
                new ProcessadorSemestralServico(
                        semestrais,
                        aulas,
                        alunos,
                        professores,
                        versoes,
                        interacoes,
                        documentos,
                        fila,
                        transacao,
                        relogio);
        processador.registrarComplementar(semestral);
        return semestral;
    }

    @Bean
    ListarSemestresDisponiveisCasoDeUso listarSemestresDisponiveisCasoDeUso(
            AutorizacaoServico autorizacao,
            AlunoRepositorio alunos,
            RelatorioAulaRepositorio aulas,
            RelogioPorta relogio) {
        return new ListarSemestresDisponiveisCasoDeUso(autorizacao, alunos, aulas, relogio);
    }

    @Bean
    ObterPreviaSemestralCasoDeUso obterPreviaSemestralCasoDeUso(
            AutorizacaoServico autorizacao,
            AlunoRepositorio alunos,
            RelatorioAulaRepositorio aulas,
            RelatorioSemestralRepositorio semestrais,
            FilaTrabalhoPorta fila) {
        return new ObterPreviaSemestralCasoDeUso(autorizacao, alunos, aulas, semestrais, fila);
    }

    @Bean
    GerarRelatorioSemestralCasoDeUso gerarRelatorioSemestralCasoDeUso(
            ObterPreviaSemestralCasoDeUso previa, FilaTrabalhoPorta fila, RelogioPorta relogio) {
        return new GerarRelatorioSemestralCasoDeUso(previa, fila, relogio);
    }

    @Bean
    SolicitarAlteracaoRelatorioSemestralCasoDeUso solicitarAlteracaoRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            RelatorioSemestralRepositorio semestrais,
            EnvioAudioServico envioAudio,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new SolicitarAlteracaoRelatorioSemestralCasoDeUso(
                acesso, semestrais, envioAudio, fila, relogio);
    }

    @Bean
    AprovarRelatorioSemestralCasoDeUso aprovarRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            RelatorioSemestralRepositorio semestrais,
            VersaoRelatorioRepositorio versoes,
            GeracaoDocumentoServico documentos,
            RelogioPorta relogio) {
        return new AprovarRelatorioSemestralCasoDeUso(
                acesso, semestrais, versoes, documentos, relogio);
    }

    @Bean
    CancelarRelatorioSemestralCasoDeUso cancelarRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            RelatorioSemestralRepositorio semestrais,
            FilaTrabalhoPorta fila,
            RelogioPorta relogio) {
        return new CancelarRelatorioSemestralCasoDeUso(acesso, semestrais, fila, relogio);
    }

    @Bean
    ObterRelatorioSemestralCasoDeUso obterRelatorioSemestralCasoDeUso(
            AcessoSemestralServico acesso,
            InteracaoRelatorioRepositorio interacoes,
            VersaoRelatorioRepositorio versoes,
            DocumentoPdfRepositorio documentos,
            GeracaoDocumentoServico geracao,
            FilaTrabalhoPorta fila,
            ObterDocumentoRelatorioCasoDeUso obterDocumento) {
        ObterRelatorioSemestralCasoDeUso obter =
                new ObterRelatorioSemestralCasoDeUso(
                        acesso, interacoes, versoes, documentos, geracao, fila);
        obterDocumento.registrarFonteSemestral(obter);
        return obter;
    }
}
