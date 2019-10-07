/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.kie.server.common.rest.variant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Variant;



/**
 * Copied from RestEasy 2.3.6.Final with no modifications 
 * Can be deleted when RESTEASY-960 is fixed
 *  
 * {@link Variant} selection.
 *
 * @see "RFC 2296"
 */
public class ServerDrivenNegotiation
{

   private Map<MediaType, QualityValue> requestedMediaTypes = null;
   private Map<String, QualityValue> requestedCharacterSets = null;
   private Map<String, QualityValue> requestedEncodings = null;
   private Map<Locale, QualityValue> requestedLanguages = null;


   public ServerDrivenNegotiation()
   {
   }


   public void setAcceptHeaders(List<String> headerValues)
   {
      requestedMediaTypes = null;
      if (headerValues == null)
         return;
      Map<MediaType, QualityValue> requested = null;
      for (String headerValue : headerValues)
      {
         Map<MediaType, QualityValue> mapping = AcceptHeaders.getMediaTypeQualityValues(headerValue);
         if (mapping == null)
            return;
         if (requested == null)
            requested = mapping;
         else
            requested.putAll(mapping);
      }
      requestedMediaTypes = requested;
   }


   public void setAcceptCharsetHeaders(List<String> headerValues)
   {
      requestedCharacterSets = null;
      if (headerValues == null)
         return;
      Map<String, QualityValue> requested = null;
      for (String headerValue : headerValues)
      {
         Map<String, QualityValue> mapping = AcceptHeaders.getStringQualityValues(headerValue);
         if (mapping == null)
            return;
         if (requested == null)
            requested = mapping;
         else
            requested.putAll(mapping);
      }
      requestedCharacterSets = requested;
   }


   public void setAcceptEncodingHeaders(List<String> headerValues)
   {
      requestedEncodings = null;
      if (headerValues == null)
         return;
      Map<String, QualityValue> requested = null;
      for (String headerValue : headerValues)
      {
         Map<String, QualityValue> mapping = AcceptHeaders.getStringQualityValues(headerValue);
         if (mapping == null)
            return;
         if (requested == null)
            requested = mapping;
         else
            requested.putAll(mapping);
      }
      requestedEncodings = requested;
   }


   public void setAcceptLanguageHeaders(List<String> headerValues)
   {
      requestedLanguages = null;
      if (headerValues == null)
         return;
      Map<Locale, QualityValue> requested = null;
      for (String headerValue : headerValues)
      {
         Map<Locale, QualityValue> mapping = AcceptHeaders.getLocaleQualityValues(headerValue);
         if (mapping == null)
            return;
         if (requested == null)
            requested = mapping;
         else
            requested.putAll(mapping);
      }
      requestedLanguages = requested;
   }


   public Variant getBestMatch(List<Variant> available)
   {
      BigDecimal bestQuality = BigDecimal.ZERO;
      Variant bestOption = null;
      for (Variant option : available)
      {
         VariantQuality quality = new VariantQuality();
         if (!applyMediaType(option, quality))
            continue;
         if (!applyCharacterSet(option, quality))
            continue;
         if (!applyEncoding(option, quality))
            continue;
         if (!applyLanguage(option, quality))
            continue;

         BigDecimal optionQuality = quality.getOverallQuality();
         if (isBetterOption(bestQuality, bestOption, optionQuality, option))
         {
            bestQuality = optionQuality;
            bestOption = option;
         }
      }
      return bestOption;
   }


   /**
    * Tests whether {@code option} is preferable over the current {@code bestOption}.
    */
   private static boolean isBetterOption(BigDecimal bestQuality, Variant best,
                                         BigDecimal optionQuality, Variant option)
   {
      if (best == null)
         return true;
      MediaType bestType = best.getMediaType();
      MediaType optionType = option.getMediaType();
      if (bestType != null && optionType != null)
      {
         if (bestType.getType().equals(optionType.getType()))
         {
            // Same type
            if (bestType.getSubtype().equals(optionType.getSubtype()))
            {
                    // Same subtype
                    // if quality is the same we prefer less parameters
                    if (bestQuality.compareTo(optionQuality) == 0) {
                        return bestType.getParameters().size() > optionType.getParameters().size();
                    } else {
                        return bestQuality.compareTo(optionQuality) < 0;
                    }
            }
            else if ("*".equals(bestType.getSubtype()))
            {
               return true;   // more specific subtype
            }
            else if ("*".equals(optionType.getSubtype()))
            {
               return false;   // less specific subtype
            }
         }
         else if ("*".equals(bestType.getType()))
         {
            return true;   // more specific type
         }
         else if ("*".equals(optionType.getType()))
         {
            return false;   // less specific type;
         }
      }

      int signum = bestQuality.compareTo(optionQuality);
      if (signum != 0)
         return signum < 0;
      return getExplicitness(best) < getExplicitness(option);
   }


   private static int getExplicitness(Variant variant)
   {
      int explicitness = 0;
      if (variant.getMediaType() != null)
         ++explicitness;
      if (variant.getEncoding() != null)
         ++explicitness;
      if (variant.getLanguage() != null)
         ++explicitness;
      return explicitness;
   }


    private boolean applyMediaType(Variant option, VariantQuality quality) {
        if (requestedMediaTypes == null)
            return true;
        MediaType mediaType = option.getMediaType();
        if (mediaType == null)
            return true;

        String type = mediaType.getType();
        if ("*".equals(type))
            type = null;
        String subtype = mediaType.getSubtype();
        if ("*".equals(subtype))
            subtype = null;
        // parameters needed
        Map<String, String> parameters = mediaType.getParameters();

        QualityValue bestQuality = QualityValue.NOT_ACCEPTABLE;
        int bestMatchCount = -1;

        for (MediaType requested : requestedMediaTypes.keySet()) {
            int matchCount = 0;
            if (type != null) {
                String requestedType = requested.getType();
                if (requestedType.equals(type))
                    ++matchCount;
                else if (!"*".equals(requestedType))
                    continue;
            }
            if (subtype != null) {
                String requestedSubtype = requested.getSubtype();
                if (requestedSubtype.equals(subtype))
                    ++matchCount;
                else if (!"*".equals(requestedSubtype))
                    continue;
            }
            if (parameters != null) {
                Map<String, String> requestedParameters = requested.getParameters();
                if (!requestedParameters.isEmpty() && hasRequiredParameters(requestedParameters, parameters)) {
                    matchCount += countRequiredParameters(requestedParameters, parameters);
                }
            }

            if (matchCount > bestMatchCount) {
                bestMatchCount = matchCount;
                bestQuality = requestedMediaTypes.get(requested);
            } else if (matchCount == bestMatchCount) {
                QualityValue qualityValue = requestedMediaTypes.get(requested);
                if (bestQuality.compareTo(qualityValue) < 0)
                    bestQuality = qualityValue;
            }
        }

        if (!bestQuality.isAcceptable())
            return false;

        quality.setMatchCountQualityValue(bestMatchCount == 0 ? QualityValue.LOWEST : QualityValue.valueOf(bestMatchCount));
        quality.setMediaTypeQualityValue(bestQuality);
        return true;
    }

    private int countRequiredParameters(Map<String, String> required, Map<String, String> available) {
        int numberOfRequiredParameters = 0;
        for (Entry<String, String> requiredEntry : required.entrySet()) {
            String name = requiredEntry.getKey();
            String value = requiredEntry.getValue();
            String availableValue = available.get(name);
            // if it accepts any value or it is the same value
            if (availableValue.equals("*") || value.equals(availableValue)) {
                numberOfRequiredParameters++;
            }
        }
        return numberOfRequiredParameters;
    }

   private boolean hasRequiredParameters(Map<String, String> required, Map<String, String> available)
   {
      for (Entry<String, String> requiredEntry : required.entrySet())
      {
         String name = requiredEntry.getKey();
         String value = requiredEntry.getValue();
         String availableValue = available.get(name);
         if (availableValue == null && "charset".equals(name)) {
            if (requestedCharacterSets != null
                    && !requestedCharacterSets.containsKey(null)
                    && !requestedCharacterSets.containsKey(value)) {
               return false;
                }
            } else if (!value.equals(availableValue)) {
                return false;
         }
      }
      return true;
   }


   private boolean applyCharacterSet(Variant option, VariantQuality quality)
   {
      if (requestedCharacterSets == null)
         return true;
      MediaType mediaType = option.getMediaType();
      if (mediaType == null)
         return true;
      String charsetParameter = mediaType.getParameters().get("charset");
      if (charsetParameter == null)
         return true;
      QualityValue value = requestedCharacterSets.get(charsetParameter);
      if (value == null)   // try wildcard
         value = requestedCharacterSets.get(null);
      if (value == null)   // no match
         return false;
      if (!value.isAcceptable()) return false;
      quality.setCharacterSetQualityValue(value);
      return true;
   }


   private boolean applyEncoding(Variant option, VariantQuality quality)
   {
      if (requestedEncodings == null)
         return true;
      String encoding = option.getEncoding();
      if (encoding == null)
         return true;
      QualityValue value = requestedEncodings.get(encoding);
      if (value == null)   // try wildcard
         value = requestedEncodings.get(null);
      if (value == null)   // no match
         return false;
      if (!value.isAcceptable()) return false;
      quality.setEncodingQualityValue(value);
      return true;
   }

   private boolean hasCountry(Locale locale)
   {
      return locale.getCountry() != null && !"".equals(locale.getCountry().trim());
   }


   private boolean applyLanguage(Variant option, VariantQuality quality)
   {
      if (requestedLanguages == null)
         return true;
      Locale language = option.getLanguage();
      if (language == null)
         return true;
      QualityValue value = null;
      for (Entry<Locale, QualityValue> entry : requestedLanguages.entrySet())
      {
         Locale locale = entry.getKey();
         QualityValue qualityValue = entry.getValue();
         if (locale == null) continue;

         if (locale.getLanguage().equalsIgnoreCase(language.getLanguage()))
         {
            if (hasCountry(locale) && hasCountry(language))
            {
               if (locale.getCountry().equalsIgnoreCase(language.getCountry()))
               {
                  value = qualityValue;
                  break;
               }
               else
               {
                  continue;
               }
            }
            else if (hasCountry(locale) == hasCountry(language))
            {
               value = qualityValue;
               break;
            }
            else
            {
               value = qualityValue; // might be a better match so re-loop
            }
         }
      }

      if (value == null)   // try wildcard
         value = requestedLanguages.get(null);
      if (value == null)   // no match
         return false;
      if (!value.isAcceptable()) return false;
      quality.setLanguageQualityValue(value);
      return true;
   }

}
