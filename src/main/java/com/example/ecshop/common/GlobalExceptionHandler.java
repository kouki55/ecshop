package com.example.ecshop.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller で起きた例外をまとめて処理し、利用者には汎用のエラー画面（ステータスコードだけ）を返す。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handle(Exception ex) {
        HttpStatusCode status = statusOf(ex);
        if (status.is5xxServerError()) {
            // 例外メッセージには入力値（メールアドレスなど）が含まれることがあるので、ログには種類と発生場所だけを出す
            // 改行が混ざると偽のログ行を作られるおそれがあるので、念のため取り除く（ログインジェクション対策）
            log.error("処理中に例外が発生しました: {}", describe(ex).replace('\r', ' ').replace('\n', ' '));
        }
        return new ModelAndView("error", Map.of("status", status.value()), status);
    }

    private static HttpStatusCode statusOf(Exception ex) {
        // 「パラメータ不足（400）」「メソッド違い（405）」「見つからない（404）」など、状態コードが決まっている例外はその値を使う
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode();
        }
        // URL の ID が数字でないなど、値の型が合わない場合は利用者の入力の誤りなので 400
        if (ex instanceof TypeMismatchException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String describe(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (!sb.isEmpty()) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getName());
            StackTraceElement[] trace = t.getStackTrace();
            if (trace.length > 0) {
                sb.append(" at ").append(trace[0]);
            }
        }
        return sb.toString();
    }
}
