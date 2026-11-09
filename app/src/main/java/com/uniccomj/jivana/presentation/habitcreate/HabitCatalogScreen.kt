package com.uniccomj.jivana.presentation.habitcreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uniccomj.jivana.R

@Composable
internal fun HabitCatalogScreen(
    expandedCategory: String?,
    onCategoryClick: (String) -> Unit,
    onTemplateClick: (HabitTemplate, String, String) -> Unit,
    onCustomClick: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.habit_catalog_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Button(
            onClick = onCustomClick,
            modifier = Modifier.fillMaxWidth().testTag("habit_catalog_custom")
        ) {
            Text(stringResource(R.string.habit_catalog_custom))
        }
        for (category in HabitCatalog) {
            OutlinedButton(
                onClick = { onCategoryClick(category.id) },
                modifier = Modifier.fillMaxWidth().testTag("habit_category_${category.id}")
            ) {
                Text(stringResource(category.title))
            }
            if (expandedCategory == category.id) {
                for (template in category.templates) {
                    val name = stringResource(template.name)
                    val description = template.description?.let { stringResource(it) }.orEmpty()
                    TextButton(
                        onClick = { onTemplateClick(template, name, description) },
                        modifier = Modifier.fillMaxWidth().testTag("habit_template_${template.id}")
                    ) {
                        Text(name)
                    }
                }
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.testTag("habit_catalog_cancel")) {
            Text(stringResource(R.string.habit_cancel))
        }
    }
}
