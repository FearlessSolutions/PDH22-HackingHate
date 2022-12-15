import com.google.cloud.functions.HttpFunction
import com.google.cloud.functions.HttpRequest
import com.google.cloud.functions.HttpResponse
import java.io.IOException

class MessageRateFunction : HttpFunction {

    @Throws(IOException::class)
    override fun service(request: HttpRequest?, response: HttpResponse?) {
        TODO("Not yet implemented")
    }
}