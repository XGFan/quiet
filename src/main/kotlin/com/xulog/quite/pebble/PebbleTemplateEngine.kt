package com.xulog.quite.pebble

import com.mitchellbosecke.pebble.PebbleEngine
import com.mitchellbosecke.pebble.error.PebbleException
import spark.ModelAndView
import spark.TemplateEngine
import java.io.IOException
import java.io.StringWriter


class PebbleTemplateEngine
constructor(val engine: PebbleEngine) : TemplateEngine() {

    private val globalModel = HashMap<String, Any>()

    fun addGlobalModel(model: Map<String, Any>): PebbleTemplateEngine {
        globalModel.putAll(model)
        return this
    }

    fun addGlobalModel(key: String, value: Any): PebbleTemplateEngine {
        globalModel[key] = value
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun render(modelAndView: ModelAndView): String {
        val model = modelAndView.model
        return if (model == null || model is Map<*, *>) {
            try {
                val writer = StringWriter()
                val template = engine.getTemplate(modelAndView.viewName)
                if (model == null) {
                    template.evaluate(writer)
                } else {
                    template.evaluate(writer, globalModel + modelAndView.model as Map<String, Any>)
                }
                writer.toString()
            } catch (e: PebbleException) {
                throw IllegalArgumentException(e)
            } catch (e: IOException) {
                throw IllegalArgumentException(e)
            }
        } else {
            throw IllegalArgumentException("Invalid model, model must be instance of Map.")
        }
    }
}