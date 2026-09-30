package br.com.felipeblaft.relatorios.configuracao;

import br.com.felipeblaft.relatorios.casodeuso.acesso.AtualizarConversaTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.ConsultarVinculoTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EncerrarSessaoWebCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EncerrarVinculoTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.EntrarPortalCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.IdentificarProfessorTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.ValidarSessaoWebCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.acesso.VincularTelegramCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.CarregarCadastroInicialCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.carga.EmitirCodigoAcessoCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.BuscarAlunosCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterInicioProfessorCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.inicio.ObterResumoAlunoCasoDeUso;
import br.com.felipeblaft.relatorios.dominio.comum.ParametrosNegocio;
import br.com.felipeblaft.relatorios.porta.AlunoRepositorio;
import br.com.felipeblaft.relatorios.porta.AssociacaoRepositorio;
import br.com.felipeblaft.relatorios.porta.GeradorCodigoAcessoPorta;
import br.com.felipeblaft.relatorios.porta.ProfessorRepositorio;
import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;
import br.com.felipeblaft.relatorios.porta.RelatoriosEmAbertoConsultaPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.SessaoWebRepositorio;
import br.com.felipeblaft.relatorios.porta.TentativaAcessoRepositorio;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import br.com.felipeblaft.relatorios.servico.AutenticacaoCodigoServico;
import br.com.felipeblaft.relatorios.servico.AutorizacaoServico;
import br.com.felipeblaft.relatorios.servico.ControleTentativasAcessoServico;
import br.com.felipeblaft.relatorios.servico.EmissaoCodigoAcessoServico;
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
            ProfessorRepositorio professores, EmissaoCodigoAcessoServico emissao, RelogioPorta relogio) {
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
}
