package com.kodcu.engine;

import com.kodcu.config.DocbookConfigBean;
import com.kodcu.config.HtmlConfigBean;
import com.kodcu.config.OdfConfigBean;
import com.kodcu.config.PreviewConfigBean;
import com.kodcu.controller.ApplicationController;
import com.kodcu.other.ConverterResult;
import com.kodcu.service.ThreadService;
import jdk.nashorn.api.scripting.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.script.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by usta on 22.08.2015.
 */
//@Lazy
@Component("NashornEngine")
public class AsciidocNashornConverter implements AsciidocConvertible {

    private final ScriptEngine scriptEngine;
    private final ApplicationController controller;
    private final DocbookConfigBean docbookConfigBean;
    private final ThreadService threadService;
    private final CompletableFuture completableFuture = new CompletableFuture();

    private Invocable invocable;

    private Logger logger = LoggerFactory.getLogger(AsciidocNashornConverter.class);
    private final PreviewConfigBean previewConfigBean;
    private final HtmlConfigBean htmlConfigBean;
    private final OdfConfigBean odfConfigBean;

    @Autowired
    public AsciidocNashornConverter(ScriptEngine scriptEngine, ApplicationController controller, DocbookConfigBean docbookConfigBean, ThreadService threadService, PreviewConfigBean previewConfigBean, HtmlConfigBean htmlConfigBean, OdfConfigBean odfConfigBean) {

        this.scriptEngine = scriptEngine;
        this.controller = controller;
        this.docbookConfigBean = docbookConfigBean;
        this.threadService = threadService;
        this.previewConfigBean = previewConfigBean;
        this.htmlConfigBean = htmlConfigBean;
        this.odfConfigBean = odfConfigBean;

        completableFuture.runAsync(() -> {
            try {

                scriptEngine.eval("var console = {  log : print, debug : print, warn : print }");
                scriptEngine.eval("var navigator = {  appVersion : 'JavaFX' }");
                scriptEngine.put("afx", this.controller);

                List<String> scripts = Arrays.asList("jade.js", "asciidoctor-all.js", "asciidoctor-image-size-info.js",
                        "asciidoctor-uml-block.js", "asciidoctor-ditaa-block.js", "asciidoctor-math-block.js",
                        "asciidoctor-tree-block.js", "asciidoctor-chart-block.js", "asciidoctor-docbook.js",
                        "asciidoctor-reveal.js", "asciidoctor-deck.js", "asciidoctor-odf.js", "optionhelper.js",
                        "outliner.js", "converters.js");


                for (String script : scripts) {

                    try (InputStream inputStream = AsciidocNashornConverter.class.getResourceAsStream("/public/js/" + script);
                         InputStreamReader reader = new InputStreamReader(inputStream);) {
                        scriptEngine.eval(reader);

                    }
                }

                this.invocable = (Invocable) scriptEngine;

                completableFuture.complete(null);

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                completableFuture.completeExceptionally(e);
            }
        }, threadService.executor());

    }

    private ConverterResult convert(String functionName, String asciidoc, String config) {

        try {
            completableFuture.join();

            Object o = invocable.invokeFunction(functionName, asciidoc, config);
            JSObject convertDocbook = (JSObject) o;
            ConverterResult converterResult = new ConverterResult(convertDocbook);
            return converterResult;
        } catch (Exception e) {
            logger.error("Problem occured while {}", functionName, e);
            throw new RuntimeException(e);
        }

    }


    @Override
    public ConverterResult convertDocbook(String asciidoc) {
        String config = docbookConfigBean.getJSON().toString();
        return convert("convertDocbook", asciidoc, config);
    }

    @Override
    public ConverterResult convertAsciidoc(String asciidoc) {
        String config = previewConfigBean.getJSON().toString();
        return convert("convertAsciidoc", asciidoc, config);
    }

    @Override
    public ConverterResult convertHtml(String asciidoc) {
        String config = htmlConfigBean.getJSON().toString();
        return convert("convertHtml", asciidoc, config);
    }

    @Override
    public void convertOdf(String asciidoc) {
        String config = odfConfigBean.getJSON().toString();
        convert("convertOdf", asciidoc, config);
    }

    @Override
    public void fillOutlines(Object doc) {
        threadService.runTaskLater(() -> {
            try {
                invocable.invokeFunction("fillOutlines", doc);
            } catch (Exception e) {
                logger.debug("Problem occured while filling outlines", e);
            }
        });
    }
}
