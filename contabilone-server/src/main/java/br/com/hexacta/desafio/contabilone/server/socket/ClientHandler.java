package br.com.hexacta.desafio.contabilone.server.socket;

import br.com.hexacta.desafio.contabilone.server.domains.MovieDTO;
import br.com.hexacta.desafio.contabilone.server.services.ApiImdbMovieService;
import br.com.hexacta.desafio.contabilone.server.services.CropperMovieService;
import br.com.hexacta.desafio.contabilone.server.services.impl.ImdbCropperMovieServiceImpl;
import br.com.hexacta.desafio.contabilone.server.services.impl.OmdbApiMovieServiceImpl;
import br.com.hexacta.desafio.contabilone.server.utils.ResponseSocketUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cliente handler para ouvir as solicitações multiplas dos clientes.
 *
 * @author Jardel Marden on 11/10/2020
 * @project ContabilOne
 */
@Slf4j
public class ClientHandler implements Runnable {

    private final Socket client;
    private final ApiImdbMovieService apiImdbMovieService;
    private final CropperMovieService cropperMovieService;

    public ClientHandler(Socket client) {

        if (Objects.isNull(client)) {
            throw new IllegalArgumentException("Socket is not null");
        }

        this.client = client;
        this.apiImdbMovieService = new OmdbApiMovieServiceImpl();
        this.cropperMovieService = new ImdbCropperMovieServiceImpl();
    }

    @Override
    public void run() {
        try {
            PrintWriter saida = new PrintWriter(client.getOutputStream(), true);

            BufferedReader leitura = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;
            String title = "";

            Pattern pattern = Pattern.compile("\\sTitle+:([0-9a-zA-Z])");

            while ((line = leitura.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    title = matcher.replaceFirst("$1");
                    break;
                }
            }

            System.out.println("Client search title: " + title);

            String result = payload(title);
            saida.println(result);

            saida.close();
            leitura.close();
            client.close();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Adicionei dois serviços de consulta para os titulos, um deles por meio
     * de uma API Rest e o segundo fluxo fazendo recortes no site https://www.imdb.com
     * <p>
     * Apos realizar a consulta é feito um merge dos resultados filtrando os registros
     * duplicados e ordenado por titulo.
     * <p>
     * A lista com os titulos é tratada para gerar um novo template e o mesmo
     * será usado como resposta para o cliente.
     *
     * @param title {@link String}
     * @throws IOException
     */
    private String payload(String title) throws IOException {
        List<MovieDTO> movies = apiImdbMovieService.findByTitle(title);

        if (Objects.nonNull(movies)) {
            movies.addAll(cropperMovieService.findByTitle(title));

            movies = movies.stream()
                    .filter(distinctBy(MovieDTO::getTitle))
                    .sorted(Comparator.comparing(MovieDTO::getTitle))
                    .collect(Collectors.toList());
        } else {
            movies = cropperMovieService.findByTitle(title);
        }

        return ResponseSocketUtil.payload(movies);
    }

    public static <T> Predicate<T> distinctBy(Function<? super T, ?> f) {
        Set<Object> objects = new ConcurrentSkipListSet<>();
        return t -> objects.add(f.apply(t));
    }
}