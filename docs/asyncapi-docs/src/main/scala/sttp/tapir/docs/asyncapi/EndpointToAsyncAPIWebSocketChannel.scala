package sttp.tapir.docs.asyncapi

import sttp.model.Method
import sttp.tapir.EndpointOutput.WebSocketBodyWrapper
import sttp.tapir.apispec.{IterableToListMap, Reference, ReferenceOr, Tag, Schema => ASchema, SchemaType => ASchemaType}
import sttp.tapir.asyncapi._
import sttp.tapir.docs.apispec.namedPathComponents
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.{Codec, CodecFormat, Endpoint, EndpointIO, EndpointInput}

import scala.collection.immutable.ListMap

class EndpointToAsyncAPIWebSocketChannel(
    schemas: Schemas,
    codecToMessageKey: Map[Codec[_, _, _ <: CodecFormat], MessageKey],
    options: AsyncAPIDocsOptions
) {
  def apply(
      e: Endpoint[_, _, _, _],
      ws: WebSocketBodyWrapper[_, _]
  ): (String, ChannelItem) = {
    val inputs = e.input.asVectorOfBasicInputs(includeAuth = false)
    val pathComponents = namedPathComponents(inputs)
    val method = e.input.method.getOrElse(Method.GET)

    val queryInputs = inputs.collect { case EndpointInput.Query(name, codec, _) => name -> schemas(codec) }
    val headerInputs = inputs.collect { case EndpointIO.Header(name, codec, _) => name -> schemas(codec) }

    val channelItem = ChannelItem(
      e.info.summary.orElse(e.info.description).orElse(ws.info.description),
      Some(endpointToOperation(options.subscribeOperationId(pathComponents, e), e, ws.wrapped.responses)),
      Some(endpointToOperation(options.publishOperationId(pathComponents, e), e, ws.wrapped.requests)),
      parameters(inputs),
      List(WebSocketChannelBinding(method.method, objectSchemaFromFields(queryInputs), objectSchemaFromFields(headerInputs), None))
    )

    (e.renderPathTemplate(renderQueryParam = None, includeAuth = false), channelItem)
  }

  private def parameters(inputs: Vector[EndpointInput.Basic[_]]): ListMap[String, ReferenceOr[Parameter]] = {
    inputs.collect { case EndpointInput.PathCapture(Some(name), codec, info) =>
      name -> Right(Parameter(info.description, schemas(codec).right.toOption, None))
    }.toListMap
  }

  private def endpointToOperation(
      id: String,
      e: Endpoint[_, _, _, _],
      codec: Codec[_, _, _ <: CodecFormat]
  ): Operation = {
    Operation(
      Some(id),
      e.info.summary,
      e.info.description,
      e.info.tags.map(Tag(_)).toList,
      None,
      Nil,
      Nil,
      codecToMessageKey.get(codec).map(mk => Left(Reference.to("#/components/messages/", mk)))
    )
  }

  private def objectSchemaFromFields(fields: Vector[(String, ReferenceOr[ASchema])]): Option[ASchema] = {
    if (fields.isEmpty) None
    else
      Some {
        ASchema(`type` = Some(ASchemaType.Object), properties = fields.toListMap)
      }
  }
}
